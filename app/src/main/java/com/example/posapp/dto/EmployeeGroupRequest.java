package com.example.posapp.dto;

import com.example.posapp.entity.EmployeeGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating an employee group over HTTP.
 * <p>
 * Enforced at the API boundary:
 * <ul>
 *   <li>{@code organizationId} is required (a group cannot exist without
 *       one) and resolved by the service against the organization
 *       repository.</li>
 *   <li>{@code name} is required and non-blank, capped at 255 characters
 *       to match the underlying column.</li>
 *   <li>{@code parentId} is optional; {@code null} creates a root group,
 *       a non-null value must reference an existing group in the same
 *       organization (resolved and validated by the service).</li>
 *   <li>{@code active} is optional; omitted means the group is active.</li>
 * </ul>
 *
 * @param organizationId the owning organization ID
 * @param name the employee group name
 * @param parentId optional parent employee group ID (null makes a root group)
 * @param active whether the employee group is currently in service (defaults to true)
 */
public record EmployeeGroupRequest(
        @NotNull Long organizationId,
        @NotBlank @Size(max = 255) String name,
        Long parentId,
        Boolean active) {

    /**
     * Map the request to a transient {@link EmployeeGroup} entity for the
     * service layer. The owning organization and the parent are not
     * mapped here: the service resolves {@code organizationId} and
     * {@code parentId} against their repositories, so the entity stays
     * HTTP-agnostic.
     * @return a new employee group with the request values and no ID
     */
    public EmployeeGroup toEntity() {
        EmployeeGroup group = new EmployeeGroup(name);
        group.setActive(active == null || active);
        return group;
    }
}
