package com.example.posapp.dto;

import com.example.posapp.entity.MenuItemModifierGroupAssignment;

/**
 * API representation of a single MenuItem ↔ ModifierGroup assignment,
 * carrying the display position used to order modifier groups within a
 * menu item along with the group's intrinsic selection policy.
 *
 * @param modifierGroupId the assigned modifier group ID
 * @param modifierGroupName the assigned modifier group name
 * @param minSelections the group's intrinsic minimum selection count
 * @param maxSelections the group's intrinsic maximum selection count
 * @param displayOrder the position of the modifier group within the item
 */
public record MenuItemModifierGroupAssignmentResponse(
        Long modifierGroupId,
        String modifierGroupName,
        int minSelections,
        int maxSelections,
        int displayOrder) {

    /**
     * Map a {@link MenuItemModifierGroupAssignment} entity to its API
     * representation.
     * @param assignment the assignment entity
     * @return the API representation
     */
    public static MenuItemModifierGroupAssignmentResponse from(MenuItemModifierGroupAssignment assignment) {
        return new MenuItemModifierGroupAssignmentResponse(
                assignment.getModifierGroup().getId(),
                assignment.getModifierGroup().getName(),
                assignment.getModifierGroup().getMinSelections(),
                assignment.getModifierGroup().getMaxSelections(),
                assignment.getDisplayOrder());
    }
}
