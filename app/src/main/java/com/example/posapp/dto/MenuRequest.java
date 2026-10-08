package com.example.posapp.dto;

import com.example.posapp.entity.Menu;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a menu over HTTP.
 * <p>
 * Enforced at the API boundary: the name must be provided and non-blank,
 * and it is capped at 255 characters to match the underlying column.
 * {@code active} is optional; omitted means the menu is active, following
 * the same convention used by {@link MenuItemRequest}.
 * </p>
 *
 * @param name the menu name
 * @param active whether the menu is currently in service (defaults to true)
 */
public record MenuRequest(
        @NotBlank @Size(max = 255) String name,
        Boolean active) {

    /**
     * Map the request to a transient {@link Menu} entity for the service layer.
     * @return a new menu with the request values and no ID
     */
    public Menu toEntity() {
        Menu menu = new Menu(name);
        menu.setActive(active == null || active);
        return menu;
    }
}
