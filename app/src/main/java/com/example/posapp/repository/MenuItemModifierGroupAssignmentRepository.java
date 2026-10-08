package com.example.posapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.MenuItemModifierGroupAssignment;

/**
 * Repository interface for managing {@link MenuItemModifierGroupAssignment}
 * entities, the join records linking {@link com.example.posapp.entity.MenuItem}s
 * to {@link com.example.posapp.entity.ModifierGroup}s.
 * <p>
 * Provides lookups by menu item (ordered by display position) and by the
 * (item, group) pair used to detect duplicate assignments.
 * </p>
 */
public interface MenuItemModifierGroupAssignmentRepository
        extends JpaRepository<MenuItemModifierGroupAssignment, Long> {

    /**
     * Find all assignments for a menu item, ordered by their display position.
     * @param menuItemId the menu item ID
     * @return the list of assignments in display order
     */
    List<MenuItemModifierGroupAssignment> findByMenuItemIdOrderByDisplayOrder(Long menuItemId);

    /**
     * Find the assignment linking a specific menu item to a specific modifier group.
     * @param menuItemId the menu item ID
     * @param modifierGroupId the modifier group ID
     * @return an Optional containing the assignment if it exists
     */
    Optional<MenuItemModifierGroupAssignment> findByMenuItemIdAndModifierGroupId(
            Long menuItemId, Long modifierGroupId);

    /**
     * Check whether the given menu item already has the given modifier group assigned.
     * @param menuItemId the menu item ID
     * @param modifierGroupId the modifier group ID
     * @return {@code true} if an assignment already exists
     */
    boolean existsByMenuItemIdAndModifierGroupId(Long menuItemId, Long modifierGroupId);

    /**
     * Count assignments referencing a given modifier group. Used by the
     * ModifierGroup delete guard so a group still attached to a menu item
     * is rejected at the service layer instead of surfacing a raw FK
     * violation.
     * @param modifierGroupId the modifier group ID
     * @return the number of assignments referencing the modifier group
     */
    long countByModifierGroupId(Long modifierGroupId);

    /**
     * Count assignments referencing a given menu item. Used by the MenuItem
     * delete guard so an item still carrying modifier groups is rejected at
     * the service layer instead of surfacing a raw FK violation.
     * @param menuItemId the menu item ID
     * @return the number of assignments referencing the menu item
     */
    long countByMenuItemId(Long menuItemId);
}
