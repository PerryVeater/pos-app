package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.EmployeePinCredential;

/**
 * Repository interface for managing {@link EmployeePinCredential}
 * entities, the secret-bearing companion records of
 * {@link com.example.posapp.entity.Employee}s.
 * <p>
 * The credential is keyed on the employee because
 * {@code uk_employee_pin_credential_employee} allows at most one row per
 * employee, so every lookup the PIN service performs goes through the
 * employee ID. The repository is deliberately kept away from general
 * employee reads: only the PIN write path and the assignment PIN branch
 * consult it.
 * </p>
 */
public interface EmployeePinCredentialRepository
        extends JpaRepository<EmployeePinCredential, Long> {

    /**
     * Find the credential belonging to the given employee.
     * @param employeeId the employee ID
     * @return an Optional containing the credential if the employee has a PIN
     */
    Optional<EmployeePinCredential> findByEmployeeId(Long employeeId);

    /**
     * Check whether the given employee currently holds a PIN credential.
     * Used by the assignment flow to decide whether adding a store also has
     * to materialize a PIN claim at that store.
     * @param employeeId the employee ID
     * @return {@code true} if the employee has a credential row
     */
    boolean existsByEmployeeId(Long employeeId);

    /**
     * Delete the credential of the given employee, if any. Used by the PIN
     * removal flow; the matching {@link EmployeePinStoreScope} rows are
     * deleted explicitly by the writer because scope rows do not cascade
     * from the credential table.
     * @param employeeId the employee ID
     */
    void deleteByEmployeeId(Long employeeId);
}
