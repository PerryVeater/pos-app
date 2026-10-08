package com.example.posapp.dto;

import com.example.posapp.entity.MenuGroupAssignment;

/**
 * API representation of a single menu ↔ menu group assignment, carrying
 * the display position used to order groups within a menu.
 *
 * @param menuGroupId the assigned menu group ID
 * @param menuGroupName the assigned menu group name
 * @param displayOrder the position of the group within its menu
 */
public record MenuGroupAssignmentResponse(
        Long menuGroupId,
        String menuGroupName,
        int displayOrder) {

    /**
     * Map a {@link MenuGroupAssignment} entity to its API representation.
     * @param assignment the assignment entity
     * @return the API representation
     */
    public static MenuGroupAssignmentResponse from(MenuGroupAssignment assignment) {
        return new MenuGroupAssignmentResponse(
                assignment.getMenuGroup().getId(),
                assignment.getMenuGroup().getName(),
                assignment.getDisplayOrder());
    }
}
