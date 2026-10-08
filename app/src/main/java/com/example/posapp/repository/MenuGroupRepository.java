package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.MenuGroup;

/**
 * Repository interface for managing {@link MenuGroup} entities.
 * <p>
 * Extends {@code JpaRepository} so the standard CRUD operations are
 * available. Adds name-based lookups used by the service layer for
 * duplicate-name validation.
 * </p>
 */
public interface MenuGroupRepository extends JpaRepository<MenuGroup, Long> {

    /**
     * Find a menu group by its unique name.
     * @param name the menu group name
     * @return an Optional containing the menu group if found, or empty if not
     */
    Optional<MenuGroup> findByName(String name);

    /**
     * Check whether a menu group with the given name already exists.
     * @param name the menu group name
     * @return {@code true} if a menu group with the name exists
     */
    boolean existsByName(String name);

    /**
     * Check whether a menu group with the given name exists, excluding the
     * group identified by {@code id}. Used by update to allow keeping the
     * same name.
     * @param name the menu group name
     * @param id the ID of the menu group being updated
     * @return {@code true} if another menu group with the name exists
     */
    boolean existsByNameAndIdNot(String name, Long id);
}
