package com.example.posapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.Employee;

/**
 * Repository interface for managing {@link Employee} entities.
 * <p>
 * Extends {@code JpaRepository} so the standard CRUD operations are
 * available. Adds the organization-scoped lookups used by the service
 * layer: the email uniqueness checks that back
 * {@code uk_employee_organization_email}, and the employee count used by
 * the Organization delete guard. No name-based lookup is exposed because
 * employee names are not unique.
 * </p>
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Check whether an employee with the given email already exists in the
     * given organization. Only meaningful for a non-null email: employees
     * without an email are exempt from the uniqueness rule.
     * @param organizationId the owning organization ID
     * @param email the normalized email to look for
     * @return {@code true} when an employee in the organization uses the email
     */
    boolean existsByOrganizationIdAndEmail(Long organizationId, String email);

    /**
     * Check whether a different employee (excluding {@code id}) already
     * uses the given email in the organization, so an update may keep its
     * own email while conflicts with other employees are rejected.
     * @param organizationId the owning organization ID
     * @param email the normalized email to look for
     * @param id the employee ID to exclude from the check
     * @return {@code true} when another employee in the organization uses the email
     */
    boolean existsByOrganizationIdAndEmailAndIdNot(Long organizationId, String email, Long id);

    /**
     * Count the employees owned by the given organization. Used by the
     * Organization delete guard so an organization with attached employees
     * is rejected at the service layer instead of surfacing a raw FK
     * violation.
     * @param organizationId the owning organization ID
     * @return the number of employees referencing the organization
     */
    long countByOrganizationId(Long organizationId);
}
