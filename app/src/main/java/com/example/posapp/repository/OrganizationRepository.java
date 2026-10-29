package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Page through the organization with the given ID.
     * <p>
     * The brand-scoped listing holds exactly one row, but it still pages so
     * that every list endpoint shares one contract: a page beyond the first
     * is empty rather than an error, and the row count is reported. The
     * {@code Is} suffix keeps this distinct from {@code CrudRepository}'s
     * single-result {@code findById}.
     * </p>
     * @param id the organization ID
     * @param pageable the page to serve
     * @return the requested page, holding the organization only on page 0
     */
    Page<Organization> findByIdIs(Long id, Pageable pageable);

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
