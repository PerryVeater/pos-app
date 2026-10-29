package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Page through the assignments of a menu, ordered by display position.
     * <p>
     * {@code display_order} is not unique, so the order alone cannot place a
     * row on exactly one page. The service passes a page request whose sort
     * is appended after this clause, and the primary key in it breaks the
     * tie.
     * </p>
     * @param menuId the menu ID
     * @param pageable the page to serve, sorted to break display-order ties
     * @return the requested page of assignments in display order
     */
    Page<MenuGroupAssignment> findByMenuIdOrderByDisplayOrder(Long menuId, Pageable pageable);

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
