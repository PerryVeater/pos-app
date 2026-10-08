package com.example.posapp.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for attaching a modifier group to a menu item with a
 * specific display position.
 *
 * @param modifierGroupId the ID of the modifier group to assign; required
 * @param displayOrder the position of the modifier group within the item; required
 */
public record MenuItemModifierGroupAssignmentRequest(
        @NotNull Long modifierGroupId,
        @NotNull Integer displayOrder) {
}
