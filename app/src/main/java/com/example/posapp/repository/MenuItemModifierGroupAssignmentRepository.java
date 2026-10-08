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
     * Count assignments referencing a given modifier group. Reserved for
     * future guards that restrict deleting groups still visible on a menu
     * item.
     * @param modifierGroupId the modifier group ID
     * @return the number of assignments referencing the modifier group
     */
    long countByModifierGroupId(Long modifierGroupId);
}
