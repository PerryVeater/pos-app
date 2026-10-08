package com.example.posapp.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for attaching a modifier to a modifier group with a specific
 * display position.
 *
 * @param modifierId the ID of the modifier to assign; required
 * @param displayOrder the position of the modifier within the group; required
 */
public record ModifierAssignmentRequest(
        @NotNull Long modifierId,
        @NotNull Integer displayOrder) {
}
