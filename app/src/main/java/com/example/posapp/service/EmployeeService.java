package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Organization;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeeValidationException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.repository.EmployeeGroupMembershipRepository;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.OrganizationRepository;

/**
 * Service layer for {@link Employee} entities.
 * <p>
 * Encapsulates the employee business rules: the name is required; every
 * employee must reference an existing owning {@link Organization} on both
 * create and update (moving an employee to a different organization is
 * allowed as long as the new owner exists and the employee does not still
 * belong to any employee group, because memberships are pinned to the
 * employee's organization through V14's composite foreign keys); the
 * optional email must be unique within the owning organization when
 * present, while any number of employees may leave it unset. Deleting an
 * employee is guarded against attached employee group memberships so a raw
 * foreign-key violation never surfaces to the client; the organization
 * delete guard keeps an organization alive while it still owns employees.
 * </p>
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepo;
    private final OrganizationRepository organizationRepo;
    private final EmployeeGroupMembershipRepository membershipRepo;

    /**
     * Constructor for EmployeeService.
     * @param employeeRepo the repository for employees
     * @param organizationRepo the repository for organizations, used to
     *        resolve the owning organization on create and update
     * @param membershipRepo the repository for employee group memberships,
     *        used by the delete guard
     */
    public EmployeeService(EmployeeRepository employeeRepo,
                           OrganizationRepository organizationRepo,
                           EmployeeGroupMembershipRepository membershipRepo) {
        this.employeeRepo = employeeRepo;
        this.organizationRepo = organizationRepo;
        this.membershipRepo = membershipRepo;
    }

    /**
     * Create a new employee after validating its fields and resolving the
     * owning organization.
     * @param employee the transient employee to save (its organization
     *        field is overwritten by the resolved organization)
     * @param organizationId the ID of the owning organization, required
     * @return the saved employee
     * @throws EmployeeValidationException if the name is blank or the
     *         email is already used by another employee in the organization
     * @throws OrganizationNotFoundException if the organization ID is
     *         missing or does not exist
     */
    public Employee createEmployee(Employee employee, Long organizationId) {
        Organization organization = resolveOrganization(organizationId);
        validateRequiredFields(employee);
        String normalizedEmail = normalizeEmail(employee.getEmail());
        employee.setEmail(normalizedEmail);
        if (normalizedEmail != null
                && employeeRepo.existsByOrganizationIdAndEmail(organizationId, normalizedEmail)) {
            throw new EmployeeValidationException(
                    "Email already exists in organization: organizationId="
                            + organizationId + ", email=" + normalizedEmail);
        }
        employee.setOrganization(organization);
        return employeeRepo.save(employee);
    }

    /**
     * Update an existing employee, including moving it to a different
     * organization. The email is validated against the target
     * organization, so moving an employee into an organization that
     * already uses its email is rejected.
     * @param id the employee ID
     * @param updated the replacement values
     * @param organizationId the new owning organization ID (required; may
     *        equal the current owner)
     * @return the updated employee
     * @throws EmployeeNotFoundException if no employee exists with the ID
     * @throws OrganizationNotFoundException if the organization ID is
     *         missing or does not exist
     * @throws EmployeeValidationException if the name is blank or the
     *         email conflicts with another employee in the target
     *         organization
     */
    public Employee updateEmployee(Long id, Employee updated, Long organizationId) {
        Organization organization = resolveOrganization(organizationId);
        validateRequiredFields(updated);
        String normalizedEmail = normalizeEmail(updated.getEmail());
        updated.setEmail(normalizedEmail);
        return employeeRepo.findById(id)
                .map(existing -> {
                    if (normalizedEmail != null
                            && employeeRepo.existsByOrganizationIdAndEmailAndIdNot(
                                    organizationId, normalizedEmail, id)) {
                        throw new EmployeeValidationException(
                                "Email already exists in organization: organizationId="
                                        + organizationId + ", email=" + normalizedEmail);
                    }
                    rejectOrganizationChangeWithMemberships(existing, organizationId, id);
                    existing.setName(updated.getName());
                    existing.setEmail(normalizedEmail);
                    existing.setActive(updated.isActive());
                    existing.setOrganization(organization);
                    return employeeRepo.save(existing);
                })
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    /**
     * Refuse to move an employee that still belongs to any employee group
     * into a different organization. The V14 migration pins every
     * membership row to the employee's organization through a composite
     * foreign key, so a silent cross-organization update would be rejected
     * by the database with a raw {@code fk_egm_employee} violation. Doing
     * the check here turns that into a clean domain-level 400 and gives
     * callers the opportunity to detach the memberships first. Keeping the
     * employee inside its current organization (including no-op updates
     * where the target organization equals the existing one) is always
     * allowed, and employees without memberships can move freely. The
     * guard is fail-safe: it short-circuits only when the current and
     * target organization IDs are both known to be equal, so an
     * unexpectedly unloaded organization graph (a null
     * {@code getOrganization()} or a null {@code getId()}) still runs the
     * membership count rather than silently permitting a move that the
     * database would later reject.
     * @param existing the persisted employee about to be updated
     * @param targetOrganizationId the organization ID requested by the caller
     * @param id the employee ID, used for the membership count and error text
     * @throws EmployeeValidationException if the organization is changing
     *         and the employee still has memberships
     */
    private void rejectOrganizationChangeWithMemberships(Employee existing,
                                                         Long targetOrganizationId,
                                                         Long id) {
        Long currentOrganizationId = existing.getOrganization() == null
                ? null : existing.getOrganization().getId();
        // Skip the count only when we can prove the organization is not
        // changing; anything else (different ID or unknown current ID)
        // falls through so the guard still rejects the update when a
        // membership exists.
        if (currentOrganizationId != null && currentOrganizationId.equals(targetOrganizationId)) {
            return;
        }
        if (membershipRepo.countByEmployeeId(id) > 0) {
            throw new EmployeeValidationException(
                    "Cannot move employee to another organization while employee group"
                            + " memberships exist: employeeId=" + id
                            + ", currentOrganizationId=" + currentOrganizationId
                            + ", targetOrganizationId=" + targetOrganizationId);
        }
    }

    /**
     * Delete an employee by ID. Refuses to delete an employee that still
     * belongs to at least one employee group so callers must remove the
     * memberships first.
     * @param id the employee ID
     * @throws EmployeeNotFoundException if no employee exists with the ID
     * @throws EmployeeValidationException if the employee still has group memberships
     */
    public void deleteEmployee(Long id) {
        if (!employeeRepo.existsById(id)) {
            throw new EmployeeNotFoundException(id);
        }
        if (membershipRepo.countByEmployeeId(id) > 0) {
            throw new EmployeeValidationException(
                    "Cannot delete employee still belonging to an employee group: " + id);
        }
        employeeRepo.deleteById(id);
    }

    /**
     * Retrieve every employee.
     * @return the list of employees
     */
    public List<Employee> getAllEmployees() {
        return employeeRepo.findAll();
    }

    /**
     * Retrieve an employee by ID.
     * @param id the employee ID
     * @return the Optional containing the employee if found
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<Employee> getEmployeeById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return employeeRepo.findById(id);
    }

    /**
     * Load the referenced organization or fail with
     * {@link OrganizationNotFoundException}. A null ID is treated the same
     * way as a missing organization so callers cannot bypass the FK
     * requirement.
     * @param organizationId the ID to resolve (may be null)
     * @return the persisted organization
     */
    private Organization resolveOrganization(Long organizationId) {
        if (organizationId == null) {
            throw new OrganizationNotFoundException(null);
        }
        return organizationRepo.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }

    /**
     * Reject missing or blank values for the required employee fields,
     * mirroring the API boundary rules.
     * @param employee the employee to inspect
     * @throws EmployeeValidationException if the name is missing or blank
     */
    private static void validateRequiredFields(Employee employee) {
        if (employee.getName() == null || employee.getName().isBlank()) {
            throw new EmployeeValidationException("Name must be provided");
        }
    }

    /**
     * Trim the email and normalize empty strings to {@code null} so
     * callers can send either an omitted field or an empty string to mean
     * "no email", and the uniqueness check only runs when a real value is
     * supplied. The value is not case-folded, matching the stores.email
     * convention.
     * @param raw the raw email (may be null or blank)
     * @return the trimmed email, or {@code null} when unset
     */
    private static String normalizeEmail(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
