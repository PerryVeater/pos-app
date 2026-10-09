package com.example.posapp.dto;

import com.example.posapp.entity.EmployeeGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating an employee group over HTTP.
 * <p>
 * The owning organization is intentionally not part of the payload: a
 * group cannot be moved between organizations because that would break
 * the same-organization rule linking a group to its parent and children,
 * so {@code organizationId} is fixed at creation. Enforced at the API
 * boundary: {@code name} must be provided and non-blank; {@code parentId}
 * is optional — {@code null} makes the group a root, a non-null value is
 * resolved by the service and must reference a group in the same
 * organization; {@code active} is optional and omitted means the group
 * is active.
 * </p>
 *
 * @param name the employee group name
 * @param parentId optional parent employee group ID (null makes a root group)
 * @param active whether the employee group is currently in service (defaults to true)
 */
public record EmployeeGroupUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        Long parentId,
        Boolean active) {

    /**
     * Map the request to a transient {@link EmployeeGroup} entity holding
     * the replacement values for the service layer. The parent is not
     * mapped here: the service resolves {@code parentId} against the
     * employee group repository, so the entity stays HTTP-agnostic.
     * @return a new employee group with the request values and no ID
     */
    public EmployeeGroup toEntity() {
        EmployeeGroup group = new EmployeeGroup(name);
        group.setActive(active == null || active);
        return group;
    }
}
