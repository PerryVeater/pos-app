package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.Modifier;

/**
 * Repository interface for managing {@link Modifier} entities.
 * <p>
 * Extends {@code JpaRepository} so the standard CRUD operations are
 * available. Adds a name-based lookup used by callers that resolve a
 * modifier by its label; name is not enforced unique.
 * </p>
 */
public interface ModifierRepository extends JpaRepository<Modifier, Long> {

    /**
     * Find a modifier by name. Names are not enforced unique so the query
     * returns the Optional of the first matching row.
     * @param name the modifier name
     * @return an Optional containing a matching modifier, if any
     */
    Optional<Modifier> findFirstByName(String name);

    /**
     * Check whether any modifier exists with the given name.
     * @param name the modifier name
     * @return {@code true} if at least one modifier uses the name
     */
    boolean existsByName(String name);
}
