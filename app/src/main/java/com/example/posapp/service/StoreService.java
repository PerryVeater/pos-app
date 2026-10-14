package com.example.posapp.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.exception.OrganizationValidationException;
import com.example.posapp.exception.StoreNotFoundException;
import com.example.posapp.repository.EmployeeStoreAssignmentRepository;
import com.example.posapp.repository.OrganizationRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Service layer for {@link Store} entities.
 * <p>
 * Encapsulates the store business rules: name is required (but not
 * unique); an optional {@code storeNumber} is unique within the owning
 * organization when present; every store must reference an existing
 * {@link Organization} on both create and update (re-parenting is allowed
 * as long as the new owner exists and the store has no employee
 * assignments left, because assignments are pinned to the store's
 * organization through V15's composite foreign keys); the {@code country}
 * must be a real ISO 3166-1 alpha-2 code (normalized to uppercase); and
 * the IANA {@code timezone} must be a valid zone identifier recognized by
 * {@link ZoneId#getAvailableZoneIds()}. Deleting a store is guarded
 * against attached employee store assignments so a raw foreign-key
 * violation never surfaces to the client.
 * </p>
 */
@Service
public class StoreService {

    /**
     * The officially assigned ISO 3166-1 alpha-2 country codes, sourced from
     * the JDK's {@link Locale#getISOCountries()}. Country values are
     * validated against this set so a well-formed but unassigned code such
     * as {@code "XX"} is rejected even though it passes the two-letter
     * format check at the API boundary.
     */
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    private final StoreRepository storeRepo;
    private final OrganizationRepository organizationRepo;
    private final EmployeeStoreAssignmentRepository assignmentRepo;

    /**
     * Constructor for StoreService.
     * @param storeRepo the repository for stores
     * @param organizationRepo the repository for organizations, used to
     *        resolve the owning organization on create and update
     * @param assignmentRepo the repository for employee store assignments,
     *        used by the delete and organization-change guards
     */
    public StoreService(StoreRepository storeRepo,
                        OrganizationRepository organizationRepo,
                        EmployeeStoreAssignmentRepository assignmentRepo) {
        this.storeRepo = storeRepo;
        this.organizationRepo = organizationRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /**
     * Create a new store after validating its fields and resolving the
     * owning organization.
     * @param store the transient store to save (its organization field is
     *        overwritten by the resolved organization)
     * @param organizationId the ID of the owning organization, required
     * @return the saved store
     * @throws OrganizationValidationException if the name is blank, an
     *         address field is missing, the timezone is not a valid IANA
     *         identifier, or the store number is already used in the
     *         organization
     * @throws OrganizationNotFoundException if the organization ID is missing
     *         or does not exist
     */
    public Store createStore(Store store, Long organizationId) {
        Organization organization = resolveOrganization(organizationId);
        validateRequiredFields(store);
        store.setCountry(normalizeAndValidateCountry(store.getCountry()));
        validateTimezone(store.getTimezone());
        String normalizedNumber = normalizeStoreNumber(store.getStoreNumber());
        store.setStoreNumber(normalizedNumber);
        if (normalizedNumber != null
                && storeRepo.existsByOrganizationIdAndStoreNumber(organizationId, normalizedNumber)) {
            throw new OrganizationValidationException(
                    "Store number already exists in organization: organizationId="
                            + organizationId + ", storeNumber=" + normalizedNumber);
        }
        store.setOrganization(organization);
        return storeRepo.save(store);
    }

    /**
     * Update an existing store, including re-parenting it to a different
     * organization. The store number is validated against the target
     * organization, so re-parenting a store into an organization that
     * already uses its number is rejected. Re-parenting is refused while
     * the store still has employee assignments.
     * @param id the store ID
     * @param updated the replacement values
     * @param organizationId the new owning organization ID (required; may
     *        equal the current owner)
     * @return the updated store
     * @throws StoreNotFoundException if no store exists with the ID
     * @throws OrganizationNotFoundException if the organization ID is missing
     *         or does not exist
     * @throws OrganizationValidationException if a required field is missing,
     *         the timezone is invalid, the store number conflicts with
     *         another store in the target organization, or the organization
     *         is changing while the store still has employee assignments
     */
    public Store updateStore(Long id, Store updated, Long organizationId) {
        Organization organization = resolveOrganization(organizationId);
        validateRequiredFields(updated);
        updated.setCountry(normalizeAndValidateCountry(updated.getCountry()));
        validateTimezone(updated.getTimezone());
        String normalizedNumber = normalizeStoreNumber(updated.getStoreNumber());
        updated.setStoreNumber(normalizedNumber);
        return storeRepo.findById(id)
                .map(existing -> {
                    if (normalizedNumber != null
                            && storeRepo.existsByOrganizationIdAndStoreNumberAndIdNot(
                                    organizationId, normalizedNumber, id)) {
                        throw new OrganizationValidationException(
                                "Store number already exists in organization: organizationId="
                                        + organizationId + ", storeNumber=" + normalizedNumber);
                    }
                    rejectOrganizationChangeWithAssignments(existing, organizationId, id);
                    existing.setName(updated.getName());
                    existing.setActive(updated.isActive());
                    existing.setOrganization(organization);
                    existing.setStoreNumber(normalizedNumber);
                    existing.setCountry(updated.getCountry());
                    existing.setStateProvince(updated.getStateProvince());
                    existing.setCity(updated.getCity());
                    existing.setAddressLine1(updated.getAddressLine1());
                    existing.setAddressLine2(updated.getAddressLine2());
                    existing.setPostalCode(updated.getPostalCode());
                    existing.setPhoneNumber(updated.getPhoneNumber());
                    existing.setEmail(updated.getEmail());
                    existing.setTimezone(updated.getTimezone());
                    return storeRepo.save(existing);
                })
                .orElseThrow(() -> new StoreNotFoundException(id));
    }

    /**
     * Refuse to move a store that still has employee assignments into a
     * different organization. The V15 migration pins every assignment row
     * to the store's organization through a composite foreign key, so a
     * silent cross-organization update would be rejected by the database
     * with a raw {@code fk_esa_store} violation. Doing the check here
     * turns that into a clean domain-level 400 and gives callers the
     * opportunity to detach the assignments first. Mirrors
     * {@code EmployeeService.rejectOrganizationChangeWithAttachments} and
     * is likewise fail-safe: the count runs whenever the organization is
     * not provably unchanged.
     * @param existing the persisted store about to be updated
     * @param targetOrganizationId the organization ID requested by the caller
     * @param id the store ID, used for the assignment count and error text
     * @throws OrganizationValidationException if the organization is
     *         changing and the store still has assigned employees
     */
    private void rejectOrganizationChangeWithAssignments(Store existing,
                                                         Long targetOrganizationId,
                                                         Long id) {
        Long currentOrganizationId = existing.getOrganization() == null
                ? null : existing.getOrganization().getId();
        // Skip the count only when we can prove the organization is not
        // changing; anything else (different ID or unknown current ID)
        // falls through so the guard still rejects the update when an
        // assignment exists.
        if (currentOrganizationId != null && currentOrganizationId.equals(targetOrganizationId)) {
            return;
        }
        if (assignmentRepo.countByStoreId(id) > 0) {
            throw new OrganizationValidationException(
                    "Cannot move store to another organization while employee"
                            + " assignments exist: storeId=" + id
                            + ", currentOrganizationId=" + currentOrganizationId
                            + ", targetOrganizationId=" + targetOrganizationId);
        }
    }

    /**
     * Delete a store by ID. Refuses to delete a store that still has
     * employee assignments so callers must remove the assignments first.
     * A missing store is reported as not found rather than being the
     * silent no-op {@code deleteById} would otherwise perform, matching
     * the employee and employee group delete semantics.
     * @param id the store ID
     * @throws StoreNotFoundException if no store exists with the ID
     * @throws OrganizationValidationException if the store still has
     *         employee assignments
     */
    public void deleteStore(Long id) {
        if (!storeRepo.existsById(id)) {
            throw new StoreNotFoundException(id);
        }
        if (assignmentRepo.countByStoreId(id) > 0) {
            throw new OrganizationValidationException(
                    "Cannot delete store still having assigned employees: " + id);
        }
        storeRepo.deleteById(id);
    }

    /**
     * Retrieve every store.
     * @return the list of stores
     */
    public List<Store> getAllStores() {
        return storeRepo.findAll();
    }

    /**
     * Retrieve a store by ID.
     * @param id the store ID
     * @return the Optional containing the store if found
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<Store> getStoreById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return storeRepo.findById(id);
    }

    /**
     * Load the referenced organization or fail with
     * {@link OrganizationNotFoundException}. A null ID is treated the same
     * way as a missing organization so callers cannot bypass the FK
     * requirement.
     * @param organizationId the ID to resolve (may be null)
     * @return the persisted organization
     */
    private Organization resolveOrganization(Long organizationId) {
        if (organizationId == null) {
            throw new OrganizationNotFoundException(null);
        }
        return organizationRepo.findById(organizationId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }

    /**
     * Reject missing or blank values for the required store fields: the
     * human-readable name plus every address component and the timezone.
     * Optional fields are not touched here.
     * @param store the store to inspect
     * @throws OrganizationValidationException if any required field is
     *         missing or blank
     */
    private static void validateRequiredFields(Store store) {
        requireText(store.getName(), "Name must be provided");
        requireText(store.getCountry(), "Country is required");
        requireText(store.getStateProvince(), "State/province is required");
        requireText(store.getCity(), "City is required");
        requireText(store.getAddressLine1(), "Address line 1 is required");
        requireText(store.getPostalCode(), "Postal code is required");
        requireText(store.getTimezone(), "Timezone is required");
    }

    /**
     * Verify that {@code timezone} is a well-known IANA region identifier.
     * Fixed-offset values such as {@code "+08:00"}, {@code "-08:00"} and
     * {@code "Z"} are rejected even though {@link ZoneId#of(String)} parses
     * them successfully, because they cannot express daylight-saving
     * transitions; the stored value must be a region-based zone name such
     * as {@code America/Los_Angeles} or {@code America/Mexico_City}.
     * @param timezone the timezone string to validate
     * @throws OrganizationValidationException if the value is a UTC offset
     *         or otherwise not a valid IANA zone identifier
     */
    private static void validateTimezone(String timezone) {
        if (looksLikeUtcOffset(timezone)) {
            throw new OrganizationValidationException(
                    "Timezone must be a valid IANA identifier (not a UTC offset): " + timezone);
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new OrganizationValidationException(
                    "Timezone must be a valid IANA identifier: " + timezone);
        }
    }

    /**
     * Recognise the ISO-8601 offset forms accepted by {@link ZoneId#of(String)}
     * plus the special {@code "Z"} shorthand. Region names never start with
     * {@code +} or {@code -}, so the leading sign plus the absence of a
     * slash is a reliable signal.
     * @param timezone a non-null timezone string
     * @return {@code true} when the value looks like a fixed UTC offset
     */
    private static boolean looksLikeUtcOffset(String timezone) {
        if (timezone.length() < 3) {
            return timezone.equals("Z") || timezone.equals("z");
        }
        char first = timezone.charAt(0);
        return (first == '+' || first == '-') && Character.isDigit(timezone.charAt(1));
    }

    /**
     * Trim the store number and normalize empty strings to {@code null} so
     * callers can send either an omitted field or an empty string to mean
     * "not set", and the uniqueness check only runs when a real value is
     * supplied.
     * @param raw the raw store number (may be null or blank)
     * @return the trimmed store number, or {@code null} when unset
     */
    private static String normalizeStoreNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Normalize the country to an uppercase ISO 3166-1 alpha-2 value and
     * reject codes that are not officially assigned. Trims surrounding
     * whitespace and upper-cases with {@link Locale#ROOT} so the stored value
     * is stable regardless of the caller's locale, then checks membership in
     * {@link #ISO_COUNTRIES}. A well-formed but unassigned code such as
     * {@code "XX"} is rejected here even though the API-boundary pattern only
     * enforces the two-letter shape.
     * @param country the raw country value (already required non-blank)
     * @return the trimmed, upper-cased ISO 3166-1 alpha-2 code
     * @throws OrganizationValidationException if the code is not an assigned
     *         ISO 3166-1 alpha-2 element
     */
    private static String normalizeAndValidateCountry(String country) {
        String normalized = country.trim().toUpperCase(Locale.ROOT);
        if (!ISO_COUNTRIES.contains(normalized)) {
            throw new OrganizationValidationException(
                    "Country must be a valid ISO 3166-1 alpha-2 code: " + country);
        }
        return normalized;
    }

    /**
     * Reject blank or missing text for a required field.
     * @param value the value to check
     * @param message the exception message when the value is blank
     */
    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new OrganizationValidationException(message);
        }
    }
}
