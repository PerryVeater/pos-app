package com.example.posapp.dto;

import com.example.posapp.entity.MenuGroup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a menu group over HTTP.
 * <p>
 * Enforced at the API boundary: the name must be provided and non-blank,
 * and it is capped at 255 characters to match the underlying column.
 * {@code active} is optional; omitted means the group is active.
 * </p>
 *
 * @param name the menu group name
 * @param active whether the menu group is currently in service (defaults to true)
 */
public record MenuGroupRequest(
        @NotBlank @Size(max = 255) String name,
        Boolean active) {

    /**
     * Map the request to a transient {@link MenuGroup} entity for the service
     * layer.
     * @return a new menu group with the request values and no ID
     */
    public MenuGroup toEntity() {
        MenuGroup group = new MenuGroup(name);
        group.setActive(active == null || active);
        return group;
    }
}
