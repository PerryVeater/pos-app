package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.Menu;

/**
 * Repository interface for managing {@link Menu} entities.
 * <p>
 * Extends {@code JpaRepository} so the standard CRUD operations are
 * available. Adds name-based lookups used by the service layer for
 * duplicate-name validation and by callers that resolve a menu by name.
 * </p>
 */
public interface MenuRepository extends JpaRepository<Menu, Long> {

    /**
     * Find a menu by its unique name.
     * @param name the menu name
     * @return an Optional containing the menu if found, or empty if not
     */
    Optional<Menu> findByName(String name);

    /**
     * Check whether a menu with the given name already exists.
     * @param name the menu name
     * @return {@code true} if a menu with the name exists
     */
    boolean existsByName(String name);

    /**
     * Check whether a menu with the given name exists, excluding the menu
     * identified by {@code id}. Used by update to allow keeping the same
     * name.
     * @param name the menu name
     * @param id the ID of the menu being updated
     * @return {@code true} if another menu with the name exists
     */
    boolean existsByNameAndIdNot(String name, Long id);
}
