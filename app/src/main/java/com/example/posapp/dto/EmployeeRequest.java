package com.example.posapp.dto;

import com.example.posapp.entity.Employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating an employee over HTTP.
 * <p>
 * Enforced at the API boundary:
 * <ul>
 *   <li>{@code organizationId} is required (an employee cannot exist
 *       without one) and resolved by the service against the organization
 *       repository on both create and update.</li>
 *   <li>{@code name} is required and non-blank but not unique.</li>
 *   <li>{@code email} is optional and validated only when provided; when
 *       set it must be unique within the owning organization.
 *       Whitespace-only values are treated as unset.</li>
 *   <li>{@code active} defaults to true when omitted.</li>
 * </ul>
 * A single payload type is used for create and update, mirroring the
 * store convention: an update may move the employee to a different
 * organization, and the email uniqueness check runs against the target
 * organization.
 *
 * @param organizationId the owning organization ID
 * @param name the human-readable employee name
 * @param email optional contact email, unique within the organization when present
 * @param active whether the employee is currently in service (defaults to true)
 */
public record EmployeeRequest(
        @NotNull Long organizationId,
        @NotBlank @Size(max = 255) String name,
        @Email @Size(max = 255) String email,
        Boolean active) {

    /**
     * Map the request to a transient {@link Employee} entity for the
     * service layer. The owning organization is not mapped here: the
     * service resolves {@code organizationId} against the organization
     * repository, so the entity stays HTTP-agnostic.
     * @return a new employee with the request values and no ID
     */
    public Employee toEntity() {
        Employee employee = new Employee(name);
        employee.setActive(active == null || active);
        employee.setEmail(blankToNull(email));
        return employee;
    }

    /**
     * Trim the value and collapse empty strings to {@code null} so an
     * optional email supplied as {@code ""} behaves the same as an
     * omitted field. The value is not case-folded, matching the
     * stores.email convention.
     * @param raw the raw value
     * @return the trimmed value, or {@code null} when empty
     */
    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
