package com.example.posapp.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request body for attaching a menu group to a menu with a specific
 * display position.
 *
 * @param menuGroupId the ID of the menu group to assign; required
 * @param displayOrder the position of the group within the menu; required
 */
public record MenuGroupAssignmentRequest(
        @NotNull Long menuGroupId,
        @NotNull Integer displayOrder) {
}
