package com.example.posapp.dto;

import com.example.posapp.entity.Organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating an organization over HTTP.
 * <p>
 * Enforced at the API boundary: the name must be provided and non-blank,
 * and it is capped at 255 characters to match the underlying column.
 * {@code active} is optional; omitted means the organization is active,
 * following the same convention used by {@link MenuRequest}.
 * </p>
 *
 * @param name the organization name
 * @param active whether the organization is currently in service (defaults to true)
 */
public record OrganizationRequest(
        @NotBlank @Size(max = 255) String name,
        Boolean active) {

    /**
     * Map the request to a transient {@link Organization} entity for the
     * service layer.
     * @return a new organization with the request values and no ID
     */
    public Organization toEntity() {
        Organization organization = new Organization(name);
        organization.setActive(active == null || active);
        return organization;
    }
}
