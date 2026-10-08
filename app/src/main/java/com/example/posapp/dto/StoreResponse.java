package com.example.posapp.dto;

import com.example.posapp.entity.Store;

/**
 * API representation of a store returned by the store endpoints.
 * <p>
 * Flattens the owning organization to its {@code organizationId} and
 * {@code organizationName} so the HTTP contract stays decoupled from the
 * JPA object graph and does not require an organization lookup on every
 * store payload. Address, contact, and timezone fields are exposed
 * directly.
 * </p>
 *
 * @param id the store ID
 * @param organizationId the owning organization ID
 * @param organizationName the owning organization name
 * @param storeNumber optional human/business identifier
 * @param name the human-readable store name
 * @param active whether the store is currently in service
 * @param country ISO 3166-1 alpha-2 country code
 * @param stateProvince state or province
 * @param city city
 * @param addressLine1 primary street address
 * @param addressLine2 optional secondary street address
 * @param postalCode postal code
 * @param phoneNumber optional phone number
 * @param email optional contact email
 * @param timezone IANA time zone identifier
 */
public record StoreResponse(
        Long id,
        Long organizationId,
        String organizationName,
        String storeNumber,
        String name,
        boolean active,
        String country,
        String stateProvince,
        String city,
        String addressLine1,
        String addressLine2,
        String postalCode,
        String phoneNumber,
        String email,
        String timezone) {

    /**
     * Map a {@link Store} entity to its API representation.
     * @param store the store entity
     * @return the API representation
     */
    public static StoreResponse from(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getOrganization() == null ? null : store.getOrganization().getId(),
                store.getOrganization() == null ? null : store.getOrganization().getName(),
                store.getStoreNumber(),
                store.getName(),
                store.isActive(),
                store.getCountry(),
                store.getStateProvince(),
                store.getCity(),
                store.getAddressLine1(),
                store.getAddressLine2(),
                store.getPostalCode(),
                store.getPhoneNumber(),
                store.getEmail(),
                store.getTimezone());
    }
}
