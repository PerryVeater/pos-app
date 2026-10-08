package com.example.posapp.dto;

import java.util.List;

import com.example.posapp.entity.ModifierGroup;
import com.example.posapp.entity.ModifierGroupAssignment;

/**
 * API representation of a modifier group returned by the modifier group
 * endpoints.
 * <p>
 * Includes the group's assigned modifiers in display order via
 * {@link ModifierAssignmentResponse}. The HTTP contract stays decoupled
 * from the JPA entity.
 * </p>
 *
 * @param id the modifier group ID
 * @param name the modifier group name
 * @param minSelections the minimum number of modifiers required
 * @param maxSelections the maximum number of modifiers allowed
 * @param active whether the group is currently in service
 * @param modifiers the assigned modifiers, ordered by display position
 */
public record ModifierGroupResponse(
        Long id,
        String name,
        int minSelections,
        int maxSelections,
        boolean active,
        List<ModifierAssignmentResponse> modifiers) {

    /**
     * Map a {@link ModifierGroup} entity to its API representation. Callers
     * supply the assignments in display order so the mapping stays a straight
     * copy.
     * @param group the modifier group entity
     * @param assignments the group's assignments, already in display order
     * @return the API representation
     */
    public static ModifierGroupResponse from(ModifierGroup group, List<ModifierGroupAssignment> assignments) {
        return new ModifierGroupResponse(
                group.getId(),
                group.getName(),
                group.getMinSelections(),
                group.getMaxSelections(),
                group.isActive(),
                assignments.stream().map(ModifierAssignmentResponse::from).toList());
    }
}
