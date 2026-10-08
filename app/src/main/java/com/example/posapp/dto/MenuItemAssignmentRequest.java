package com.example.posapp.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for attaching a menu item to a menu group with a specific
 * display position.
 *
 * @param menuItemId the ID of the menu item to assign; required
 * @param displayOrder the position of the item within its group; required
 */
public record MenuItemAssignmentRequest(
        @NotNull Long menuItemId,
        @NotNull Integer displayOrder) {
}
