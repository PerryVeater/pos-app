package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.ModifierGroup;

/**
 * Repository interface for managing {@link ModifierGroup} entities.
 * <p>
 * Extends {@code JpaRepository} so the standard CRUD operations are
 * available. Adds name-based lookups used by the service layer for
 * duplicate-name validation.
 * </p>
 */
public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, Long> {

    /**
     * Find a modifier group by its unique name.
     * @param name the modifier group name
     * @return an Optional containing the group if found
     */
    Optional<ModifierGroup> findByName(String name);

    /**
     * Check whether a modifier group with the given name already exists.
     * @param name the modifier group name
     * @return {@code true} if a group with the name exists
     */
    boolean existsByName(String name);

    /**
     * Check whether a modifier group with the given name exists, excluding
     * the group identified by {@code id}. Used by update to allow keeping
     * the same name.
     * @param name the modifier group name
     * @param id the ID of the group being updated
     * @return {@code true} if another group with the name exists
     */
    boolean existsByNameAndIdNot(String name, Long id);
}
