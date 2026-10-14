package com.example.posapp.service;

import java.util.List;
import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeeStoreAssignment;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeeStoreValidationException;
import com.example.posapp.exception.StoreNotFoundException;
import com.example.posapp.repository.EmployeeStoreAssignmentRepository;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Service layer for the Employee ↔ Store assignment lifecycle.
 * <p>
 * The assignment is intentionally separated from {@link EmployeeService}
 * and {@link StoreService} so the existing aggregates stay untouched, per
 * the project's assignment-layer convention. Every write must reference an
 * existing employee and an existing store that share the same owning
 * {@link com.example.posapp.entity.Organization}: a missing aggregate is
 * reported as 404 through {@link EmployeeNotFoundException} or
 * {@link StoreNotFoundException}, and a cross-organization pair is
 * reported as 400 through {@link EmployeeStoreValidationException}. The
 * database enforces the same rule as a defence in depth through the
 * composite foreign keys added by the V15 migration.
 * </p>
 * <p>
 * Both mutating operations are idempotent: adding an existing assignment
 * is a no-op and removing a missing assignment is a no-op, so clients can
 * safely retry PUT/DELETE without a prior GET. The add path also survives
 * a concurrent write: the initial existence check, the insert, and the
 * recovery read each run in their own short transaction (the method is
 * deliberately not wrapped in an outer {@code @Transactional}), so if
 * another caller commits the same pair between our check and our insert
 * the {@code uk_employee_store_assignment} violation is caught, only
 * rethrown when it is not the pair-uniqueness constraint, and translated
 * into a re-read that returns the winner's row. The database uniqueness
 * constraint therefore remains the final safeguard rather than being
 * bypassed. Listing the stores of an employee or the employees of a store
 * requires the aggregate to exist and returns the peers resolved through
 * the join table.
 * </p>
 */
@Service
public class EmployeeStoreAssignmentService {

    private final EmployeeRepository employeeRepo;
    private final StoreRepository storeRepo;
    private final EmployeeStoreAssignmentRepository assignmentRepo;

    /**
     * Constructor for EmployeeStoreAssignmentService.
     * @param employeeRepo the repository for employees
     * @param storeRepo the repository for stores
     * @param assignmentRepo the repository for employee ↔ store assignments
     */
    public EmployeeStoreAssignmentService(EmployeeRepository employeeRepo,
                                          StoreRepository storeRepo,
                                          EmployeeStoreAssignmentRepository assignmentRepo) {
        this.employeeRepo = employeeRepo;
        this.storeRepo = storeRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /**
     * Assign an employee to a store. Re-assigning an existing pair is a
     * no-op so the endpoint is idempotent, including when a concurrent
     * caller commits the same pair between our existence check and our
     * insert. The employee and the store must belong to the same
     * organization.
     * <p>
     * No outer {@code @Transactional} wraps this method: the two reads
     * and the insert each run in Spring Data's per-call transaction, so
     * a duplicate-key failure rolls back only the losing insert and the
     * recovery re-read still sees the concurrently committed row. Any
     * {@link DataIntegrityViolationException} that is not the pair
     * uniqueness constraint is rethrown unchanged so unrelated integrity
     * errors keep surfacing as 400 "Conflicting data".
     * </p>
     * @param employeeId the employee ID
     * @param storeId the store ID
     * @return the existing or newly saved assignment
     * @throws EmployeeNotFoundException if the employee does not exist
     * @throws StoreNotFoundException if the store does not exist
     * @throws EmployeeStoreValidationException if the pair crosses organizations
     */
    public EmployeeStoreAssignment assignEmployeeToStore(Long employeeId, Long storeId) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        Store store = storeRepo.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));
        if (!sameOrganization(employee, store)) {
            throw new EmployeeStoreValidationException(
                    "Employee and store must belong to the same organization: employeeId="
                            + employeeId + ", storeId=" + storeId);
        }
        return assignmentRepo.findByEmployeeIdAndStoreId(employeeId, storeId)
                .orElseGet(() -> insertOrRecoverConcurrentDuplicate(employee, store,
                        employeeId, storeId));
    }

    /**
     * Attempt the insert and, if a concurrent request committed the same
     * (employee, store) pair in the meantime, fall back to reading the
     * winner's row back so the caller still sees success. Only
     * {@code uk_employee_store_assignment} violations are swallowed;
     * anything else (for example a tampered {@code organization_id}
     * triggering {@code fk_esa_employee}) is rethrown so the global
     * handler can still surface it as a client conflict.
     * @param employee the resolved employee
     * @param store the resolved store
     * @param employeeId the employee ID used for the recovery read
     * @param storeId the store ID used for the recovery read
     * @return the newly saved assignment or the concurrently created one
     */
    private EmployeeStoreAssignment insertOrRecoverConcurrentDuplicate(Employee employee,
                                                                       Store store,
                                                                       Long employeeId,
                                                                       Long storeId) {
        try {
            return assignmentRepo.saveAndFlush(new EmployeeStoreAssignment(employee, store));
        } catch (DataIntegrityViolationException ex) {
            if (!isDuplicatePairViolation(ex)) {
                throw ex;
            }
            return assignmentRepo.findByEmployeeIdAndStoreId(employeeId, storeId)
                    .orElseThrow(() -> ex);
        }
    }

    /**
     * Detect whether the given exception was raised by the pair-uniqueness
     * constraint rather than by any other integrity rule on the same
     * table. PostgreSQL's duplicate-key error text includes the
     * constraint name somewhere in the wrapped cause chain (leaf
     * {@code SQLException}, a Hibernate {@code ConstraintViolationException}
     * in the middle, and Spring's translated
     * {@code DataIntegrityViolationException} on top). Walking every
     * link, rather than only {@code getMostSpecificCause()}, keeps the
     * recovery reliable when any one layer changes its message format
     * while the others still name the constraint. Because the
     * {@code uk_} prefix is unique to the pair-uniqueness constraint on
     * this table, a false positive against a different integrity failure
     * (for example {@code fk_esa_employee}) is not possible without that
     * exact substring appearing in an unrelated message, and any such
     * message still resolves correctly via the follow-up recovery read.
     * @param ex the caught exception
     * @return {@code true} when any element of the chain reports the pair
     *         uniqueness constraint
     */
    private static boolean isDuplicatePairViolation(DataIntegrityViolationException ex) {
        for (Throwable cursor = ex; cursor != null; cursor = cursor.getCause()) {
            String message = cursor.getMessage();
            if (message != null && message.contains("uk_employee_store_assignment")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Remove an employee's assignment to a store. Removing a missing
     * assignment is a no-op so the endpoint is idempotent. Neither the
     * employee nor the store is deleted.
     * @param employeeId the employee ID
     * @param storeId the store ID
     * @throws EmployeeNotFoundException if the employee does not exist
     * @throws StoreNotFoundException if the store does not exist
     * @throws EmployeeStoreValidationException if the pair crosses organizations
     */
    @Transactional
    public void removeEmployeeFromStore(Long employeeId, Long storeId) {
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
        Store store = storeRepo.findById(storeId)
                .orElseThrow(() -> new StoreNotFoundException(storeId));
        if (!sameOrganization(employee, store)) {
            throw new EmployeeStoreValidationException(
                    "Employee and store must belong to the same organization: employeeId="
                            + employeeId + ", storeId=" + storeId);
        }
        assignmentRepo.findByEmployeeIdAndStoreId(employeeId, storeId)
                .ifPresent(assignmentRepo::delete);
    }

    /**
     * List the stores an employee is assigned to.
     * @param employeeId the employee ID
     * @return the stores the employee is assigned to (empty when none)
     * @throws EmployeeNotFoundException if the employee does not exist
     */
    public List<Store> listStoresForEmployee(Long employeeId) {
        if (!employeeRepo.existsById(employeeId)) {
            throw new EmployeeNotFoundException(employeeId);
        }
        return assignmentRepo.findByEmployeeId(employeeId).stream()
                .map(EmployeeStoreAssignment::getStore)
                .toList();
    }

    /**
     * List the employees assigned to a store.
     * @param storeId the store ID
     * @return the employees assigned to the store (empty when none)
     * @throws StoreNotFoundException if the store does not exist
     */
    public List<Employee> listEmployeesForStore(Long storeId) {
        if (!storeRepo.existsById(storeId)) {
            throw new StoreNotFoundException(storeId);
        }
        return assignmentRepo.findByStoreId(storeId).stream()
                .map(EmployeeStoreAssignment::getEmployee)
                .toList();
    }

    /**
     * Compare the owning organization IDs of an employee and a store.
     * Both sides are required to reference a persisted organization at
     * creation, so the null-safe comparison is defensive against a
     * partially loaded graph rather than a real schema option.
     * @param employee the resolved employee
     * @param store the resolved store
     * @return {@code true} when both belong to the same organization
     */
    private static boolean sameOrganization(Employee employee, Store store) {
        Long employeeOrgId = employee.getOrganization() == null ? null : employee.getOrganization().getId();
        Long storeOrgId = store.getOrganization() == null ? null : store.getOrganization().getId();
        return Objects.equals(employeeOrgId, storeOrgId);
    }
}
