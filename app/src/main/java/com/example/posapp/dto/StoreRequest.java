package com.example.posapp.dto;

import java.util.Locale;

import com.example.posapp.entity.Store;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a store over HTTP.
 * <p>
 * Enforced at the API boundary:
 * <ul>
 *   <li>{@code organizationId} is required (a store cannot exist without
 *       one) and resolved by the service against the organization
 *       repository.</li>
 *   <li>{@code name} is required and non-blank but not unique.</li>
 *   <li>{@code storeNumber} is optional; when provided it must be unique
 *       within the owning organization. Whitespace-only values are treated
 *       as unset.</li>
 *   <li>Address components follow sensible length limits aligned to the
 *       underlying columns; {@code country} is an ISO 3166-1 alpha-2 code.</li>
 *   <li>{@code email} is optional and validated only when provided.</li>
 *   <li>{@code timezone} is required and must be an IANA identifier; the
 *       service parses it with {@link java.time.ZoneId}.</li>
 * </ul>
 *
 * @param organizationId the owning organization ID
 * @param storeNumber optional human/business identifier unique within the organization
 * @param name the human-readable store name
 * @param active whether the store is currently in service (defaults to true)
 * @param country ISO 3166-1 alpha-2 country code
 * @param stateProvince required state or province
 * @param city required city
 * @param addressLine1 required primary street address
 * @param addressLine2 optional secondary street address
 * @param postalCode required postal code (string, may include letters)
 * @param phoneNumber optional phone number (string, may include formatting)
 * @param email optional contact email
 * @param timezone required IANA time zone identifier
 */
public record StoreRequest(
        @NotNull Long organizationId,
        @Size(max = 64) String storeNumber,
        @NotBlank @Size(max = 255) String name,
        Boolean active,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be an ISO 3166-1 alpha-2 code") String country,
        @NotBlank @Size(max = 80) String stateProvince,
        @NotBlank @Size(max = 80) String city,
        @NotBlank @Size(max = 200) String addressLine1,
        @Size(max = 200) String addressLine2,
        @NotBlank @Size(max = 20) String postalCode,
        @Size(max = 32) String phoneNumber,
        @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 64) String timezone) {

    /**
     * Map the request to a transient {@link Store} entity for the service
     * layer. The owning organization is not mapped here: the service
     * resolves {@code organizationId} against the organization repository,
     * so the entity stays HTTP-agnostic. The country code is normalized to
     * uppercase.
     * @return a new store with the request values, no ID, and no organization
     */
    public Store toEntity() {
        Store store = new Store(null, name,
                country == null ? null : country.trim().toUpperCase(Locale.ROOT),
                stateProvince, city, addressLine1, postalCode, timezone);
        store.setActive(active == null || active);
        store.setStoreNumber(blankToNull(storeNumber));
        store.setAddressLine2(blankToNull(addressLine2));
        store.setPhoneNumber(blankToNull(phoneNumber));
        store.setEmail(blankToNull(email));
        return store;
    }

    /**
     * Trim the value and collapse empty strings to {@code null} so optional
     * fields supplied as {@code ""} behave the same as omitted fields.
     * @param raw the raw value
     * @return the trimmed value, or {@code null} when empty
     */
    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
