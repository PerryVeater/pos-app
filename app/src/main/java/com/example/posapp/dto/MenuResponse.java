package com.example.posapp.dto;

import java.util.List;

import com.example.posapp.entity.Menu;
import com.example.posapp.entity.MenuGroupAssignment;

/**
 * API representation of a menu returned by the menu endpoints.
 * <p>
 * Includes the menu's assigned groups in display order via
 * {@link MenuGroupAssignmentResponse}. The HTTP contract stays decoupled
 * from the JPA entity.
 * </p>
 *
 * @param id the menu ID
 * @param name the menu name
 * @param active whether the menu is currently in service
 * @param groups the assigned menu groups, ordered by display position
 */
public record MenuResponse(
        Long id,
        String name,
        boolean active,
        List<MenuGroupAssignmentResponse> groups) {

    /**
     * Map a {@link Menu} entity to its API representation. Callers supply the
     * assignments in display order so the mapping stays a straight copy.
     * @param menu the menu entity
     * @param assignments the menu's assignments, already in display order
     * @return the API representation
     */
    public static MenuResponse from(Menu menu, List<MenuGroupAssignment> assignments) {
        return new MenuResponse(
                menu.getId(),
                menu.getName(),
                menu.isActive(),
                assignments.stream().map(MenuGroupAssignmentResponse::from).toList());
    }
}
