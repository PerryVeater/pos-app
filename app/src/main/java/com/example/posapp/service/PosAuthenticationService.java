package com.example.posapp.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.posapp.dto.PosAuthenticateResponse;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeePinCredential;
import com.example.posapp.entity.EmployeeStoreAssignment;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.PosAuthFailedException;
import com.example.posapp.exception.PosAuthRateLimitedException;
import com.example.posapp.repository.EmployeePinCredentialRepository;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.EmployeeStoreAssignmentRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Orchestrates POS PIN authentication at a store and hands a verified
 * employee to {@link PosSessionService} for session issuance.
 * <p>
 * Ordering and information discipline are the whole design:
 * <ol>
 *   <li>a locked-out {@code (store, client)} pair is refused with
 *       {@link PosAuthRateLimitedException} <em>before</em> any credential is
 *       touched, so throttling never hints at whether a PIN was right;</li>
 *   <li>the store must exist and be active, and a live employee assigned to
 *       it must present a PIN that verifies against their stored hash;
 *       because PINs are unique per store (the store-scoped uniqueness rule),
 *       at most one assigned employee can match at a given store;</li>
 *   <li>every failure - missing store, inactive store, no assignment, no
 *       credential, wrong PIN, inactive employee - collapses to one
 *       {@link PosAuthFailedException}, and the failed attempt is counted.</li>
 * </ol>
 * Nothing here reveals which of those conditions occurred, and the raw PIN is
 * only ever used transiently for verification; it is never logged, stored, or
 * returned. The service is intentionally non-transactional: repository reads
 * run per call and session issuance opens its own transaction, mirroring the
 * existing non-transactional orchestrator convention.
 * </p>
 */
@Service
public class PosAuthenticationService {

    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeStoreAssignmentRepository assignmentRepository;
    private final EmployeePinCredentialRepository credentialRepository;
    private final PinHasher pinHasher;
    private final PosAuthRateLimiter rateLimiter;
    private final PosSessionService sessionService;

    /**
     * Constructor for PosAuthenticationService.
     * @param storeRepository store lookups
     * @param employeeRepository employee lookups (active flag and name)
     * @param assignmentRepository assignment lookups for the store
     * @param credentialRepository PIN credential reads
     * @param pinHasher the PIN verifier
     * @param rateLimiter the per-store/client attempt limiter
     * @param sessionService the session issuer
     */
    public PosAuthenticationService(StoreRepository storeRepository,
                                    EmployeeRepository employeeRepository,
                                    EmployeeStoreAssignmentRepository assignmentRepository,
                                    EmployeePinCredentialRepository credentialRepository,
                                    PinHasher pinHasher,
                                    PosAuthRateLimiter rateLimiter,
                                    PosSessionService sessionService) {
        this.storeRepository = storeRepository;
        this.employeeRepository = employeeRepository;
        this.assignmentRepository = assignmentRepository;
        this.credentialRepository = credentialRepository;
        this.pinHasher = pinHasher;
        this.rateLimiter = rateLimiter;
        this.sessionService = sessionService;
    }

    /**
     * Authenticate a PIN at a store and, on success, issue a session.
     * @param storeId the store being authenticated at
     * @param pin the PIN as submitted, used verbatim (leading zeros preserved)
     * @param clientKey the resolved client identifier for rate limiting
     * @return the issued session with its one-time raw bearer token
     * @throws PosAuthRateLimitedException when the client is in cooldown
     * @throws PosAuthFailedException for every credential-related failure
     */
    public PosAuthenticateResponse authenticate(Long storeId, String pin, String clientKey) {
        if (rateLimiter.isBlocked(storeId, clientKey)) {
            throw new PosAuthRateLimitedException();
        }
        // Read the store eagerly once: the loaded instance carries the active
        // flag used for the gate and the name used for the response, so no
        // uninitialized proxy is ever handed to the session writer.
        Store store = storeRepository.findById(storeId).filter(Store::isActive).orElse(null);
        Employee authenticated = store == null ? null : matchAssignedEmployee(storeId, pin);
        if (authenticated == null) {
            // A missing/inactive store and every credential mismatch land
            // here, so the failure is indistinguishable to the caller.
            rateLimiter.recordFailure(storeId, clientKey);
            throw new PosAuthFailedException();
        }
        rateLimiter.recordSuccess(storeId, clientKey);
        return sessionService.startSession(authenticated, store);
    }

    /**
     * Locate the single active employee assigned to the store whose PIN
     * verifies, or {@code null} when no assigned employee matches. Returns a
     * fully loaded employee (name and active flag initialized) so the caller
     * can build a response without lazy access.
     * @param storeId the store being authenticated at
     * @param pin the submitted PIN
     * @return the matching active employee, or {@code null}
     */
    private Employee matchAssignedEmployee(Long storeId, String pin) {
        List<EmployeeStoreAssignment> assignments = assignmentRepository.findByStoreId(storeId);
        for (EmployeeStoreAssignment assignment : assignments) {
            // Reading the id off the association proxy is free; re-loading
            // the employee gives an initialized instance for the active flag.
            Long employeeId = assignment.getEmployee().getId();
            Employee employee = employeeRepository.findById(employeeId).orElse(null);
            if (employee == null || !employee.isActive()) {
                continue;
            }
            EmployeePinCredential credential =
                    credentialRepository.findByEmployeeId(employeeId).orElse(null);
            if (credential != null && pinHasher.verify(pin, credential.getPinHash())) {
                return employee;
            }
        }
        return null;
    }
}
