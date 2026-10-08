package com.example.posapp.dto;

import com.example.posapp.entity.MenuGroup;

/**
 * API representation of a menu group returned by the menu group endpoints.
 *
 * @param id the menu group ID
 * @param name the menu group name
 * @param active whether the menu group is currently in service
 */
public record MenuGroupResponse(Long id, String name, boolean active) {

    /**
     * Map a {@link MenuGroup} entity to its API representation.
     * @param group the entity to map
     * @return the API representation
     */
    public static MenuGroupResponse from(MenuGroup group) {
        return new MenuGroupResponse(group.getId(), group.getName(), group.isActive());
    }
}
