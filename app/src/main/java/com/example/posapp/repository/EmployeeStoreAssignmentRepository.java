package com.example.posapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.EmployeeStoreAssignment;

/**
 * Repository interface for managing {@link EmployeeStoreAssignment}
 * entities, the join records linking {@link com.example.posapp.entity.Employee}s
 * to {@link com.example.posapp.entity.Store}s.
 * <p>
 * Provides the (employee, store) lookups used by the assignment service
 * to detect duplicates and locate an existing pair for removal, plus the
 * two listing queries backing the sub-resource endpoints and the counts
 * used by the Employee and Store delete and organization-change guards.
 * </p>
 */
public interface EmployeeStoreAssignmentRepository
        extends JpaRepository<EmployeeStoreAssignment, Long> {

    /**
     * Find the assignment linking a specific employee to a specific store.
     * @param employeeId the employee ID
     * @param storeId the store ID
     * @return an Optional containing the assignment if it exists
     */
    Optional<EmployeeStoreAssignment> findByEmployeeIdAndStoreId(Long employeeId, Long storeId);

    /**
     * Check whether the given employee is already assigned to the given
     * store. This is a convenience predicate exposed for callers that only
     * need a yes/no answer (currently the repository integration tests).
     * The assignment service deliberately does not use this method for
     * duplicate detection because its idempotent PUT must return the
     * existing row, so it reads through
     * {@link #findByEmployeeIdAndStoreId(Long, Long)} instead.
     * @param employeeId the employee ID
     * @param storeId the store ID
     * @return {@code true} if an assignment already exists
     */
    boolean existsByEmployeeIdAndStoreId(Long employeeId, Long storeId);

    /**
     * List every assignment for the given employee, used to expose the
     * stores the employee is assigned to.
     * @param employeeId the employee ID
     * @return the assignments (possibly empty)
     */
    List<EmployeeStoreAssignment> findByEmployeeId(Long employeeId);

    /**
     * List every assignment for the given store, used to expose the
     * employees assigned to the store.
     * @param storeId the store ID
     * @return the assignments (possibly empty)
     */
    List<EmployeeStoreAssignment> findByStoreId(Long storeId);

    /**
     * Count assignments referencing the given employee. Used by the
     * Employee delete and organization-change guards so an employee still
     * attached to any store is rejected at the service layer instead of
     * surfacing a raw FK violation.
     * @param employeeId the employee ID
     * @return the number of assignments referencing the employee
     */
    long countByEmployeeId(Long employeeId);

    /**
     * Count assignments referencing the given store. Used by the Store
     * delete and organization-change guards so a store that still has
     * assigned employees is rejected at the service layer instead of
     * surfacing a raw FK violation.
     * @param storeId the store ID
     * @return the number of assignments referencing the store
     */
    long countByStoreId(Long storeId);
}
