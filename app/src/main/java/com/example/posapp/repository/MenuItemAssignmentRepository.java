package com.example.posapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.MenuItemAssignment;

/**
 * Repository interface for managing {@link MenuItemAssignment} entities,
 * the join records linking {@link com.example.posapp.entity.MenuGroup}s to
 * {@link com.example.posapp.entity.MenuItem}s.
 * <p>
 * Provides lookups by menu group (ordered by display position) and by the
 * (group, item) pair used to detect duplicate assignments.
 * </p>
 */
public interface MenuItemAssignmentRepository extends JpaRepository<MenuItemAssignment, Long> {

    /**
     * Find all assignments for a menu group, ordered by their display position.
     * @param menuGroupId the menu group ID
     * @return the list of assignments in display order
     */
    List<MenuItemAssignment> findByMenuGroupIdOrderByDisplayOrder(Long menuGroupId);

    /**
     * Find the assignment linking a specific menu group to a specific menu item.
     * @param menuGroupId the menu group ID
     * @param menuItemId the menu item ID
     * @return an Optional containing the assignment if it exists
     */
    Optional<MenuItemAssignment> findByMenuGroupIdAndMenuItemId(Long menuGroupId, Long menuItemId);

    /**
     * Check whether the given group already has the given item assigned.
     * @param menuGroupId the menu group ID
     * @param menuItemId the menu item ID
     * @return {@code true} if an assignment already exists
     */
    boolean existsByMenuGroupIdAndMenuItemId(Long menuGroupId, Long menuItemId);

    /**
     * Count assignments referencing a given menu item. Reserved for future
     * guards that restrict deleting items still visible in a group.
     * @param menuItemId the menu item ID
     * @return the number of assignments referencing the item
     */
    long countByMenuItemId(Long menuItemId);
}
