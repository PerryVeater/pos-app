package com.example.posapp.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.Store;

/**
 * Repository interface for managing {@link Store} entities.
 * <p>
 * Extends {@code JpaRepository} so the standard CRUD operations are
 * available. Store names are not unique, so no name-based lookup is
 * exposed. Adds organization-scoped queries used by the sub-resource
 * listing, by the {@code Organization} delete guard, and by the service
 * layer's {@code storeNumber}-per-organization uniqueness rule.
 * </p>
 */
public interface StoreRepository extends JpaRepository<Store, Long> {

    /**
     * Find a store by its owning organization and business store number.
     * @param organizationId the owning organization ID
     * @param storeNumber the human/business identifier
     * @return an Optional containing the store if found
     */
    Optional<Store> findByOrganizationIdAndStoreNumber(Long organizationId, String storeNumber);

    /**
     * Check whether the given organization already owns a store with the
     * given store number. Used by the service to reject duplicates on
     * create.
     * @param organizationId the owning organization ID
     * @param storeNumber the human/business identifier
     * @return {@code true} if a store with the same number already exists
     *         in the organization
     */
    boolean existsByOrganizationIdAndStoreNumber(Long organizationId, String storeNumber);

    /**
     * Check whether another store in the given organization already uses
     * the given store number, excluding the store identified by
     * {@code id}. Used by update to allow keeping the same number.
     * @param organizationId the owning organization ID
     * @param storeNumber the human/business identifier
     * @param id the ID of the store being updated
     * @return {@code true} if another store in the organization uses the number
     */
    boolean existsByOrganizationIdAndStoreNumberAndIdNot(Long organizationId,
                                                         String storeNumber,
                                                         Long id);

    /**
     * Find every store owned by the given organization. Insertion order is
     * not guaranteed; the tenancy foundation does not carry a per-store
     * display attribute yet.
     * @param organizationId the owning organization ID
     * @return the stores owned by the organization (possibly empty)
     */
    List<Store> findByOrganizationId(Long organizationId);

    /**
     * Count stores owned by the given organization. Used by the Organization
     * delete guard so an organization with attached stores is rejected at
     * the service layer instead of surfacing a raw FK violation.
     * @param organizationId the owning organization ID
     * @return the number of stores referencing the organization
     */
    long countByOrganizationId(Long organizationId);
}
