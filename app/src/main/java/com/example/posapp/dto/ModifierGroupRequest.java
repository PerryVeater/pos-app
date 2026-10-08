package com.example.posapp.dto;

import com.example.posapp.entity.ModifierGroup;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a modifier group over HTTP.
 * <p>
 * Enforced at the API boundary: the name must be provided and non-blank,
 * and it is capped at 255 characters to match the underlying column.
 * Selection counts are non-negative; the service layer additionally
 * enforces {@code maxSelections >= minSelections}. {@code active} is
 * optional; omitted means the group is active.
 * </p>
 *
 * @param name the modifier group name
 * @param minSelections the minimum number of modifiers a customer must pick
 * @param maxSelections the maximum number of modifiers a customer may pick
 * @param active whether the group is currently in service (defaults to true)
 */
public record ModifierGroupRequest(
        @NotBlank @Size(max = 255) String name,
        @Min(0) int minSelections,
        @Min(0) int maxSelections,
        Boolean active) {

    /**
     * Map the request to a transient {@link ModifierGroup} entity for the
     * service layer.
     * @return a new modifier group with the request values and no ID
     */
    public ModifierGroup toEntity() {
        ModifierGroup group = new ModifierGroup(name, minSelections, maxSelections);
        group.setActive(active == null || active);
        return group;
    }
}
