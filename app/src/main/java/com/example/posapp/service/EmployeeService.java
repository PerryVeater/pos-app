package com.example.posapp.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Organization;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeeValidationException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.repository.EmployeeGroupMembershipRepository;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.EmployeeStoreAssignmentRepository;
import com.example.posapp.repository.OrganizationRepository;

/**
 * Service layer for {@link Employee} entities.
 * <p>
 * Encapsulates the employee business rules: the name is required; every
 * employee must reference an existing owning {@link Organization} on both
 * create and update, and that organization is the caller's brand - the
 * lookup is scoped to the brand, so a request naming another brand's
 * organization is answered exactly like one naming an organization that does
 * not exist. Because the employee being updated is itself loaded within the
 * brand, an organization change is not expressible through the back office,
 * which also means the composite foreign keys added by the V14 and V15
 * migrations can never be put at risk by a move; the
 * optional email must be unique within the owning organization when
 * present, while any number of employees may leave it unset. Deleting an
 * employee is guarded against attached employee group memberships and
 * employee store assignments so a raw foreign-key violation never surfaces
 * to the client; the organization delete guard keeps an organization alive
 * while it still owns employees.
 * </p>
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepo;
    private final OrganizationRepository organizationRepo;
    private final EmployeeGroupMembershipRepository membershipRepo;
    private final EmployeeStoreAssignmentRepository assignmentRepo;
    private final PosSessionRevoker sessionRevoker;

    /**
     * Constructor for EmployeeService.
     * @param employeeRepo the repository for employees
     * @param organizationRepo the repository for organizations, used to
     *        resolve the owning organization on create and update
     * @param membershipRepo the repository for employee group memberships,
     *        used by the delete and organization-change guards
     * @param assignmentRepo the repository for employee store assignments,
     *        used by the delete and organization-change guards
     * @param sessionRevoker revokes POS sessions when an employee is
     *        deactivated
     */
    public EmployeeService(EmployeeRepository employeeRepo,
                           OrganizationRepository organizationRepo,
                           EmployeeGroupMembershipRepository membershipRepo,
                           EmployeeStoreAssignmentRepository assignmentRepo,
                           PosSessionRevoker sessionRevoker) {
        this.employeeRepo = employeeRepo;
        this.organizationRepo = organizationRepo;
        this.membershipRepo = membershipRepo;
        this.assignmentRepo = assignmentRepo;
        this.sessionRevoker = sessionRevoker;
    }

    /**
     * Create a new employee after validating its fields and resolving the
     * owning organization.
     * @param employee the transient employee to save (its organization
     *        field is overwritten by the resolved organization)
     * @param organizationId the ID of the owning organization, required; it
     *        must be the caller's brand
     * @param brandId the organization the caller may act within
     * @return the saved employee
     * @throws EmployeeValidationException if the name is blank or the
     *         email is already used by another employee in the organization
     * @throws OrganizationNotFoundException if the organization ID is
     *         missing, does not exist, or lies outside the brand
     */
    public Employee createEmployee(Employee employee, Long organizationId, Long brandId) {
        Organization organization = resolveOrganization(organizationId, brandId);
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
     * Update an existing employee. The email is validated against the
     * owning organization, and both the employee and the requested
     * organization must belong to the caller's brand.
     * @param id the employee ID
     * @param updated the replacement values
     * @param organizationId the owning organization ID (required; must be
     *        the caller's brand, and therefore the employee's current
     *        organization)
     * @param brandId the organization the caller may act within
     * @return the updated employee
     * @throws EmployeeNotFoundException if no employee exists with the ID
     *         inside the brand
     * @throws OrganizationNotFoundException if the organization ID is
     *         missing, does not exist, or lies outside the brand
     * @throws EmployeeValidationException if the name is blank or the
     *         email conflicts with another employee in the organization
     */
    public Employee updateEmployee(Long id, Employee updated, Long organizationId, Long brandId) {
        Organization organization = resolveOrganization(organizationId, brandId);
        validateRequiredFields(updated);
        String normalizedEmail = normalizeEmail(updated.getEmail());
        updated.setEmail(normalizedEmail);
        return employeeRepo.findByIdAndOrganizationId(id, brandId)
                .map(existing -> {
                    if (normalizedEmail != null
                            && employeeRepo.existsByOrganizationIdAndEmailAndIdNot(
                                    organizationId, normalizedEmail, id)) {
                        throw new EmployeeValidationException(
                                "Email already exists in organization: organizationId="
                                        + organizationId + ", email=" + normalizedEmail);
                    }
                    // Deactivation ends every POS session immediately: a
                    // bearer token is not a shift.
                    boolean deactivating = existing.isActive() && !updated.isActive();
                    existing.setName(updated.getName());
                    existing.setEmail(normalizedEmail);
                    existing.setActive(updated.isActive());
                    existing.setOrganization(organization);
                    Employee saved = employeeRepo.save(existing);
                    if (deactivating) {
                        sessionRevoker.revokeAllForEmployee(id, Instant.now());
                    }
                    return saved;
                })
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    /**
     * Delete an employee by ID. Refuses to delete an employee that still
     * belongs to at least one employee group or is still assigned to at
     * least one store, so callers must remove the attachments first.
     * @param id the employee ID
     * @param brandId the organization the caller may act within
     * @throws EmployeeNotFoundException if no employee exists with the ID
     *         inside the brand
     * @throws EmployeeValidationException if the employee still has group
     *         memberships or store assignments
     */
    public void deleteEmployee(Long id, Long brandId) {
        if (employeeRepo.findByIdAndOrganizationId(id, brandId).isEmpty()) {
            throw new EmployeeNotFoundException(id);
        }
        if (membershipRepo.countByEmployeeId(id) > 0) {
            throw new EmployeeValidationException(
                    "Cannot delete employee still belonging to an employee group: " + id);
        }
        if (assignmentRepo.countByEmployeeId(id) > 0) {
            throw new EmployeeValidationException(
                    "Cannot delete employee still assigned to a store: " + id);
        }
        employeeRepo.deleteById(id);
    }

    /**
     * Retrieve one page of the employees in the caller's brand.
     * @param brandId the organization the caller may act within
     * @param pageable the page to serve
     * @return the requested page of that organization's employees
     */
    public Page<Employee> getAllEmployees(Long brandId, Pageable pageable) {
        return employeeRepo.findByOrganizationId(brandId, pageable);
    }

    /**
     * Retrieve an employee by ID, within the caller's brand.
     * @param id the employee ID
     * @param brandId the organization the caller may act within
     * @return the Optional containing the employee if found in that brand
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<Employee> getEmployeeById(Long id, Long brandId) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return employeeRepo.findByIdAndOrganizationId(id, brandId);
    }

    /**
     * Load the referenced organization or fail with
     * {@link OrganizationNotFoundException}. A null ID is treated the same
     * way as a missing organization so callers cannot bypass the FK
     * requirement, and an ID outside the caller's brand is treated the same
     * way as a missing one so the endpoint reveals nothing about other
     * brands.
     * @param organizationId the ID to resolve (may be null)
     * @param brandId the organization the caller may act within
     * @return the persisted organization
     */
    private Organization resolveOrganization(Long organizationId, Long brandId) {
        if (organizationId == null || !organizationId.equals(brandId)) {
            throw new OrganizationNotFoundException(organizationId);
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
