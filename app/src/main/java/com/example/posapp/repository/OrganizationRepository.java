package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.Organization;

/**
 * Repository interface for managing {@link Organization} entities.
 * <p>
 * Extends {@code JpaRepository} so the standard CRUD operations are
 * available. Adds name-based lookups used by the service layer for
 * duplicate-name validation.
 * </p>
 */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /**
     * Find an organization by its unique name.
     * @param name the organization name
     * @return an Optional containing the organization if found, or empty if not
     */
    Optional<Organization> findByName(String name);

    /**
     * Check whether an organization with the given name already exists.
     * @param name the organization name
     * @return {@code true} if an organization with the name exists
     */
    boolean existsByName(String name);

    /**
     * Check whether an organization with the given name exists, excluding the
     * organization identified by {@code id}. Used by update to allow keeping
     * the same name.
     * @param name the organization name
     * @param id the ID of the organization being updated
     * @return {@code true} if another organization with the name exists
     */
    boolean existsByNameAndIdNot(String name, Long id);
}
