package com.example.posapp.service;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeePinCredential;
import com.example.posapp.entity.EmployeePinStoreScope;
import com.example.posapp.entity.EmployeeStoreAssignment;
import com.example.posapp.entity.Store;
import com.example.posapp.repository.EmployeePinCredentialRepository;
import com.example.posapp.repository.EmployeePinStoreScopeRepository;
import com.example.posapp.repository.EmployeeStoreAssignmentRepository;

/**
 * Transactional writer owning every mutation of PIN material: the
 * {@link EmployeePinCredential} row and its {@link EmployeePinStoreScope}
 * claims.
 * <p>
 * The class exists so the atomicity boundary is explicit and small. Each
 * method runs in one transaction ({@code @Transactional} applies because
 * callers reach it through the Spring proxy from a different bean; no
 * caller is itself transactional around these calls), so a credential
 * swap or an assignment-plus-claim can never half-commit. The store-scoped
 * uniqueness rule is enforced by {@code uk_epss_store_fingerprint}; when a
 * write violates it the constraint fires inside the transaction, the whole
 * transaction rolls back, and the
 * {@link DataIntegrityViolationException} propagates to the orchestrating
 * service, which runs outside any transaction and translates it into a
 * friendly 400 without leaking the fingerprint. That is the race-safe
 * design: the database constraint, not a check-then-write in Java, is the
 * final arbiter under concurrent requests.
 * </p>
 * <p>
 * Claims are only materialized for stores the employee is actually
 * assigned to, so an employee can hold a PIN with zero claims (not yet
 * assigned anywhere) and employees without credentials never touch these
 * rows at all.
 * </p>
 */
@Service
public class EmployeeStorePinWriter {

    private final EmployeePinCredentialRepository credentialRepo;
    private final EmployeePinStoreScopeRepository scopeRepo;
    private final EmployeeStoreAssignmentRepository assignmentRepo;

    /**
     * Constructor for EmployeeStorePinWriter.
     * @param credentialRepo the credential repository
     * @param scopeRepo the store-scope claim repository
     * @param assignmentRepo the assignment repository used to locate the
     *        stores a claim must cover
     */
    public EmployeeStorePinWriter(EmployeePinCredentialRepository credentialRepo,
                                  EmployeePinStoreScopeRepository scopeRepo,
                                  EmployeeStoreAssignmentRepository assignmentRepo) {
        this.credentialRepo = credentialRepo;
        this.scopeRepo = scopeRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /**
     * Set or replace the employee's PIN atomically: upsert the credential
     * and re-materialize the store claims in one transaction, so a
     * {@code uk_epss_store_fingerprint} conflict (another employee
     * claiming the same PIN at a shared store, including a concurrent
     * setter that commits between the caller's check and this write) rolls
     * the whole operation back - the employee keeps the old secret and the
     * old claims - and escapes as {@link DataIntegrityViolationException}
     * for the non-transactional orchestrator to translate.
     * <p>
     * Claims are materialized for every store the employee is assigned to;
     * an employee with no assignments simply holds the credential without
     * claims, and claims for the previous fingerprint are replaced because
     * the row key (employee_id, store_id) is updated in place.
     * </p>
     * @param employee the owning employee
     * @param pinHash the salted password hash of the new PIN
     * @param pinFingerprint the deterministic fingerprint of the new PIN
     * @return the persisted credential
     */
    @Transactional
    public EmployeePinCredential setPin(Employee employee, String pinHash, String pinFingerprint) {
        EmployeePinCredential credential = credentialRepo.findByEmployeeId(employee.getId())
                .orElseGet(() -> new EmployeePinCredential(employee, null, null));
        credential.setPinHash(pinHash);
        credential.setPinFingerprint(pinFingerprint);
        credentialRepo.saveAndFlush(credential);
        for (EmployeeStoreAssignment assignment : assignmentRepo.findByEmployeeId(employee.getId())) {
            Long storeId = assignment.getStore().getId();
            EmployeePinStoreScope existing = scopeRepo
                    .findByEmployeeIdAndStoreId(employee.getId(), storeId).orElse(null);
            if (existing == null) {
                scopeRepo.saveAndFlush(new EmployeePinStoreScope(
                        assignment.getEmployee(), assignment.getStore(), pinFingerprint));
            } else if (!existing.getPinFingerprint().equals(pinFingerprint)) {
                // A prior PIN left a claim here; retarget it in place so
                // the (employee_id, store_id) row stays unique while the
                // new fingerprint takes over the store slot (and collides
                // with another employee's identical claim via
                // uk_epss_store_fingerprint if one exists).
                existing.setPinFingerprint(pinFingerprint);
                scopeRepo.saveAndFlush(existing);
            }
        }
        return credential;
    }

    /**
     * Delete the employee's credential and every claim materialized from
     * it, freeing the PIN at all of the employee's stores. Absence of a
     * credential is a no-op, so removal is idempotent.
     * @param employeeId the employee ID
     * @return {@code true} when a credential existed and was removed
     */
    @Transactional
    public boolean removePin(Long employeeId) {
        return credentialRepo.findByEmployeeId(employeeId)
                .map(credential -> {
                    scopeRepo.deleteAll(scopeRepo.findByEmployeeId(employeeId));
                    credentialRepo.delete(credential);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Insert an employee ↔ store assignment together with the employee's
     * PIN claim at that store, atomically. When the employee holds no
     * credential there is nothing to claim and the insert behaves exactly
     * like the plain assignment path.
     * <p>
     * Not intended to be called with an outer transaction: a fingerprint
     * conflict must roll the assignment insert back as well, which only
     * holds while this method's transaction is the widest one in play. Any
     * integrity failure - {@code uk_employee_store_assignment} or
     * {@code uk_epss_store_fingerprint} - propagates as
     * {@link DataIntegrityViolationException} after a full rollback; the
     * non-transactional caller then decides whether the failure is the
     * idempotent duplicate-pair case (recover with
     * {@link #findAssignment}) or a PIN conflict (translate to a 400).
     * </p>
     * @param employee the resolved employee
     * @param store the resolved store
     * @param pinFingerprint the employee's fingerprint, or {@code null}
     *        when the employee has no PIN
     * @return the newly saved assignment
     */
    @Transactional
    public EmployeeStoreAssignment assignWithPinClaim(Employee employee, Store store,
                                                      String pinFingerprint) {
        EmployeeStoreAssignment assignment = assignmentRepo.saveAndFlush(
                new EmployeeStoreAssignment(employee, store));
        if (pinFingerprint != null) {
            scopeRepo.saveAndFlush(new EmployeePinStoreScope(employee, store, pinFingerprint));
        }
        return assignment;
    }

    /**
     * Read one assignment pair outside any surrounding transaction, used
     * by the caller to recover when a concurrent request committed the
     * same (employee, store) pair first.
     * @param employeeId the employee ID
     * @param storeId the store ID
     * @return the assignment if one exists
     */
    @Transactional(readOnly = true)
    public Optional<EmployeeStoreAssignment> findAssignment(Long employeeId, Long storeId) {
        return assignmentRepo.findByEmployeeIdAndStoreId(employeeId, storeId);
    }

    /**
     * Read the employee's credential, if any, through the writer's own
     * transactional surface so the assignment service never touches the
     * credential repository directly.
     * @param employeeId the employee ID
     * @return the credential when the employee has a PIN
     */
    @Transactional(readOnly = true)
    public Optional<EmployeePinCredential> findCredential(Long employeeId) {
        return credentialRepo.findByEmployeeId(employeeId);
    }

    /**
     * Detect the assignment pair-uniqueness constraint anywhere in the
     * cause chain, matching the detector used by
     * {@code EmployeeStoreAssignmentService}.
     * @param ex the caught exception
     * @return {@code true} when any element of the chain reports
     *         {@code uk_employee_store_assignment}
     */
    static boolean isDuplicatePairViolation(DataIntegrityViolationException ex) {
        for (Throwable cursor = ex; cursor != null; cursor = cursor.getCause()) {
            String message = cursor.getMessage();
            if (message != null && message.contains("uk_employee_store_assignment")) {
                return true;
            }
        }
        return false;
    }
}
