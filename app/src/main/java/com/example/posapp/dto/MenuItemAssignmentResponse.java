package com.example.posapp.dto;

import com.example.posapp.entity.MenuItemAssignment;

/**
 * API representation of a single menu group ↔ menu item assignment,
 * carrying the display position used to order items within a group.
 *
 * @param menuItemId the assigned menu item ID
 * @param menuItemName the assigned menu item name
 * @param displayOrder the position of the item within its group
 */
public record MenuItemAssignmentResponse(
        Long menuItemId,
        String menuItemName,
        int displayOrder) {

    /**
     * Map a {@link MenuItemAssignment} entity to its API representation.
     * @param assignment the assignment entity
     * @return the API representation
     */
    public static MenuItemAssignmentResponse from(MenuItemAssignment assignment) {
        return new MenuItemAssignmentResponse(
                assignment.getMenuItem().getId(),
                assignment.getMenuItem().getName(),
                assignment.getDisplayOrder());
    }
}
