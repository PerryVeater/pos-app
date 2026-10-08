package com.example.posapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.MenuGroupAssignment;

/**
 * Repository interface for managing {@link MenuGroupAssignment} entities,
 * the join records linking {@link com.example.posapp.entity.Menu}s to
 * {@link com.example.posapp.entity.MenuGroup}s.
 * <p>
 * Provides lookups by menu (ordered by display position) and by the
 * (menu, group) pair used to detect duplicate assignments.
 * </p>
 */
public interface MenuGroupAssignmentRepository extends JpaRepository<MenuGroupAssignment, Long> {

    /**
     * Find all assignments for a menu, ordered by their display position.
     * @param menuId the menu ID
     * @return the list of assignments in display order
     */
    List<MenuGroupAssignment> findByMenuIdOrderByDisplayOrder(Long menuId);

    /**
     * Find the assignment linking a specific menu to a specific menu group.
     * @param menuId the menu ID
     * @param menuGroupId the menu group ID
     * @return an Optional containing the assignment if it exists
     */
    Optional<MenuGroupAssignment> findByMenuIdAndMenuGroupId(Long menuId, Long menuGroupId);

    /**
     * Check whether the given menu already has the given group assigned.
     * @param menuId the menu ID
     * @param menuGroupId the menu group ID
     * @return {@code true} if an assignment already exists
     */
    boolean existsByMenuIdAndMenuGroupId(Long menuId, Long menuGroupId);

    /**
     * Delete every assignment for a given menu group. Used when a menu group
     * is removed so no dangling assignments remain.
     * @param menuGroupId the menu group ID
     */
    void deleteByMenuGroupId(Long menuGroupId);

    /**
     * Count assignments referencing a given menu group. Used to reject
     * deleting a group that is still assigned to at least one menu.
     * @param menuGroupId the menu group ID
     * @return the number of assignments referencing the group
     */
    long countByMenuGroupId(Long menuGroupId);
}
