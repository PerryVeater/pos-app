package com.example.posapp.dto;

import java.math.BigDecimal;

import com.example.posapp.entity.ModifierGroupAssignment;

/**
 * API representation of a single modifier group ↔ modifier assignment,
 * carrying the display position used to order modifiers within a group.
 *
 * @param modifierId the assigned modifier ID
 * @param modifierName the assigned modifier name
 * @param priceAdjustment the assigned modifier's price adjustment
 * @param displayOrder the position of the modifier within its group
 */
public record ModifierAssignmentResponse(
        Long modifierId,
        String modifierName,
        BigDecimal priceAdjustment,
        int displayOrder) {

    /**
     * Map a {@link ModifierGroupAssignment} entity to its API representation.
     * @param assignment the assignment entity
     * @return the API representation
     */
    public static ModifierAssignmentResponse from(ModifierGroupAssignment assignment) {
        return new ModifierAssignmentResponse(
                assignment.getModifier().getId(),
                assignment.getModifier().getName(),
                assignment.getModifier().getPriceAdjustment(),
                assignment.getDisplayOrder());
    }
}
