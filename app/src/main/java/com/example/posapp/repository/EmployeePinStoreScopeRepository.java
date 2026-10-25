package com.example.posapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.EmployeePinStoreScope;

/**
 * Repository interface for managing {@link EmployeePinStoreScope}
 * enforcement rows, the database-level materialization of the store-scoped
 * PIN uniqueness rule.
 * <p>
 * These rows are written and removed only by the PIN writer inside its
 * transaction. Most queries here serve that writer, which locates an
 * employee's existing claims when replacing a PIN. The one exception is
 * {@link #findByStoreIdAndPinFingerprint(Long, String)}: it is the lookup
 * the POS authentication hot path uses to turn a submitted PIN into at
 * most one employee, and it is why the table exists at all.
 * </p>
 */
public interface EmployeePinStoreScopeRepository
        extends JpaRepository<EmployeePinStoreScope, Long> {

    /**
     * Resolve the single claim row for a fingerprint presented at a store,
     * with the claiming employee fetched in the same query.
     * <p>
     * This is the authentication path's only search: {@code
     * uk_epss_store_fingerprint} is a unique index on
     * {@code (store_id, pin_fingerprint)}, so the lookup is an index probe
     * that yields at most one row, independent of how many employees are
     * assigned to the store. The employee is joined rather than lazily
     * loaded so the caller does not pay a second round trip per attempt,
     * and so the detached result still carries the active flag.
     * </p>
     * @param storeId the store being authenticated at
     * @param pinFingerprint the deterministic fingerprint of the submitted PIN
     * @return the claim and its employee, or empty when no employee at that
     *         store holds this PIN
     */
    @EntityGraph(attributePaths = "employee")
    Optional<EmployeePinStoreScope> findByStoreIdAndPinFingerprint(
            Long storeId, String pinFingerprint);

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
