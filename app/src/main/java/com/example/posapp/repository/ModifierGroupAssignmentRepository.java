package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.ModifierGroupAssignment;

/**
 * Repository interface for managing {@link ModifierGroupAssignment}
 * entities, the join records linking {@link com.example.posapp.entity.ModifierGroup}s
 * to {@link com.example.posapp.entity.Modifier}s.
 * <p>
 * Provides lookups by modifier group (ordered by display position) and by
 * the (group, modifier) pair used to detect duplicate assignments.
 * </p>
 */
public interface ModifierGroupAssignmentRepository extends JpaRepository<ModifierGroupAssignment, Long> {

    /**
     * Page through the assignments of a modifier group, ordered by display
     * position.
     * <p>
     * {@code display_order} is not unique, so the order alone cannot place a
     * row on exactly one page. The service passes a page request whose sort
     * is appended after this clause, and the primary key in it breaks the
     * tie.
     * </p>
     * @param modifierGroupId the modifier group ID
     * @param pageable the page to serve, sorted to break display-order ties
     * @return the requested page of assignments in display order
     */
    Page<ModifierGroupAssignment> findByModifierGroupIdOrderByDisplayOrder(
            Long modifierGroupId, Pageable pageable);

    /**
     * Find the assignment linking a specific modifier group to a specific modifier.
     * @param modifierGroupId the modifier group ID
     * @param modifierId the modifier ID
     * @return an Optional containing the assignment if it exists
     */
    Optional<ModifierGroupAssignment> findByModifierGroupIdAndModifierId(Long modifierGroupId, Long modifierId);

    /**
     * Check whether the given modifier group already has the given modifier assigned.
     * @param modifierGroupId the modifier group ID
     * @param modifierId the modifier ID
     * @return {@code true} if an assignment already exists
     */
    boolean existsByModifierGroupIdAndModifierId(Long modifierGroupId, Long modifierId);

    /**
     * Count assignments referencing a given modifier. Used to reject
     * deleting a modifier that is still assigned to at least one group.
     * @param modifierId the modifier ID
     * @return the number of assignments referencing the modifier
     */
    long countByModifierId(Long modifierId);
}
