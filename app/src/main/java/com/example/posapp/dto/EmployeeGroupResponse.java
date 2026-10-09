package com.example.posapp.dto;

import com.example.posapp.entity.EmployeeGroup;

/**
 * API representation of an employee group returned by the employee group
 * endpoints.
 * <p>
 * Flattens the owning organization and the parent group to their IDs so
 * the HTTP contract stays decoupled from the JPA object graph; the parent
 * is {@code null} for root groups. Child groups are intentionally not
 * embedded: they are exposed through the dedicated sub-resource at
 * {@code GET /api/v1/employee-groups/{id}/children}.
 * </p>
 *
 * @param id the employee group ID
 * @param name the employee group name
 * @param active whether the employee group is currently in service
 * @param organizationId the owning organization ID
 * @param parentId the parent employee group ID, or null for a root group
 */
public record EmployeeGroupResponse(
        Long id,
        String name,
        boolean active,
        Long organizationId,
        Long parentId) {

    /**
     * Map an {@link EmployeeGroup} entity to its API representation.
     * @param group the entity to map
     * @return the API representation
     */
    public static EmployeeGroupResponse from(EmployeeGroup group) {
        return new EmployeeGroupResponse(
                group.getId(),
                group.getName(),
                group.isActive(),
                group.getOrganization() == null ? null : group.getOrganization().getId(),
                group.getParent() == null ? null : group.getParent().getId());
    }
}
