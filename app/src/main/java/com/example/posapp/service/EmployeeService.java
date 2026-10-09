package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Organization;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeeValidationException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.OrganizationRepository;

/**
 * Service layer for {@link Employee} entities.
 * <p>
 * Encapsulates the employee business rules: the name is required; every
 * employee must reference an existing owning {@link Organization} on both
 * create and update (moving an employee to a different organization is
 * allowed as long as the new owner exists); the optional email must be
 * unique within the owning organization when present, while any number of
 * employees may leave it unset. Deleting an employee has no downstream
 * guard because nothing else references it in this foundation; the
 * organization delete guard keeps an organization alive while it still
 * owns employees.
 * </p>
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepo;
    private final OrganizationRepository organizationRepo;

    /**
     * Constructor for EmployeeService.
     * @param employeeRepo the repository for employees
     * @param organizationRepo the repository for organizations, used to
     *        resolve the owning organization on create and update
     */
    public EmployeeService(EmployeeRepository employeeRepo,
                           OrganizationRepository organizationRepo) {
        this.employeeRepo = employeeRepo;
        this.organizationRepo = organizationRepo;
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
                    existing.setName(updated.getName());
                    existing.setEmail(normalizedEmail);
                    existing.setActive(updated.isActive());
                    existing.setOrganization(organization);
                    return employeeRepo.save(existing);
                })
                .orElseThrow(() -> new EmployeeNotFoundException(id));
    }

    /**
     * Delete an employee by ID.
     * @param id the employee ID
     * @throws EmployeeNotFoundException if no employee exists with the ID
     */
    public void deleteEmployee(Long id) {
        if (!employeeRepo.existsById(id)) {
            throw new EmployeeNotFoundException(id);
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
