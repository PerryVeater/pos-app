package com.example.posapp.dto;

import com.example.posapp.entity.Organization;

/**
 * API representation of an organization returned by the organization
 * endpoints.
 * <p>
 * Stores are intentionally not embedded here: they are exposed through the
 * dedicated sub-resource at {@code GET /api/v1/organizations/{id}/stores},
 * which keeps the organization payload small and avoids pulling the full
 * store list onto every organization response.
 * </p>
 *
 * @param id the organization ID
 * @param name the organization name
 * @param active whether the organization is currently in service
 */
public record OrganizationResponse(
        Long id,
        String name,
        boolean active) {

    /**
     * Map an {@link Organization} entity to its API representation.
     * @param organization the organization entity
     * @return the API representation
     */
    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.isActive());
    }
}
