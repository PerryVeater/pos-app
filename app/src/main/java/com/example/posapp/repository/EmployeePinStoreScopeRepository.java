package com.example.posapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.EmployeePinStoreScope;

/**
 * Repository interface for managing {@link EmployeePinStoreScope}
 * enforcement rows, the database-level materialization of the store-scoped
 * PIN uniqueness rule.
 * <p>
 * These rows are written and removed only by the PIN writer inside its
 * transaction; the queries here exist so the writer can locate an employee's
 * existing claims when replacing a PIN, and so tests can observe the state
 * the writer produced. Nothing in the read path of the API exposes them.
 * </p>
 */
public interface EmployeePinStoreScopeRepository
        extends JpaRepository<EmployeePinStoreScope, Long> {

    /**
     * List every store claim carried by the given employee's PIN.
     * @param employeeId the employee ID
     * @return the scope rows (empty when the employee has no PIN or no stores)
     */
    List<EmployeePinStoreScope> findByEmployeeId(Long employeeId);

    /**
     * Find the single claim row tying an employee to one store, used to
     * make repeated assignment inserts idempotent.
     * @param employeeId the employee ID
     * @param storeId the store ID
     * @return an Optional containing the claim if it exists
     */
    Optional<EmployeePinStoreScope> findByEmployeeIdAndStoreId(Long employeeId, Long storeId);

    /**
     * Count the claims held by the given employee. Used by tests and by
     * the writer's diagnostics when re-materializing claims after a PIN
     * change.
     * @param employeeId the employee ID
     * @return the number of scope rows referencing the employee
     */
    long countByEmployeeId(Long employeeId);
}
