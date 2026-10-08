package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.exception.OrganizationValidationException;
import com.example.posapp.exception.StoreNotFoundException;
import com.example.posapp.repository.OrganizationRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Unit tests for the {@link StoreService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service in
 * isolation: required-field validation, per-organization store number
 * uniqueness, IANA timezone parsing, re-parenting rules, and delegation
 * for the read and delete operations. Store name uniqueness is no longer
 * enforced and is exercised explicitly by the "duplicate store names are
 * accepted" test. No Spring context or database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock
    private StoreRepository storeRepo;

    @Mock
    private OrganizationRepository organizationRepo;

    @InjectMocks
    private StoreService storeService;

    private static Organization acme() {
        return new Organization("Acme Restaurants");
    }

    /**
     * Build a transient store populated with valid required fields so
     * individual tests can mutate a single field to trigger a specific
     * validation failure.
     */
    private static Store validStore() {
        return new Store(null, "Downtown", "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
    }

    // --- createStore ---

    @Test
    @DisplayName("createStore: valid store is saved and attached to the resolved organization")
    void createStoreValidIsSaved() {
        Organization acme = acme();
        Store input = validStore();
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme));
        when(storeRepo.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        Store saved = storeService.createStore(input, 1L);

        assertThat(saved.getName()).isEqualTo("Downtown");
        assertThat(saved.getOrganization()).isSameAs(acme);
        assertThat(saved.isActive()).isTrue();
        verify(storeRepo).save(input);
    }

    @Test
    @DisplayName("createStore: null organizationId is rejected as OrganizationNotFoundException")
    void createStoreNullOrganizationThrows() {
        assertThatThrownBy(() -> storeService.createStore(validStore(), null))
                .isInstanceOf(OrganizationNotFoundException.class);

        verify(storeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createStore: unknown organizationId is rejected as OrganizationNotFoundException")
    void createStoreUnknownOrganizationThrows() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.createStore(validStore(), 99L))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");

        verify(storeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createStore: blank name is rejected")
    void createStoreBlankNameThrows() {
        Store input = validStore();
        input.setName("   ");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));

        assertThatThrownBy(() -> storeService.createStore(input, 1L))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessage("Name must be provided");

        verify(storeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createStore: missing required address field is rejected")
    void createStoreMissingAddressFieldThrows() {
        Store input = validStore();
        input.setCity("");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));

        assertThatThrownBy(() -> storeService.createStore(input, 1L))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessage("City is required");
    }

    @Test
    @DisplayName("createStore: ISO alpha-2 codes US and MX are accepted")
    void createStoreValidIsoCountriesAccepted() {
        Store us = validStore();
        us.setCountry("US");
        Store mx = validStore();
        mx.setCountry("MX");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));
        when(storeRepo.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(storeService.createStore(us, 1L).getCountry()).isEqualTo("US");
        assertThat(storeService.createStore(mx, 1L).getCountry()).isEqualTo("MX");
    }

    @Test
    @DisplayName("createStore: lowercase country is normalized to uppercase")
    void createStoreLowercaseCountryIsUpperCased() {
        Store input = validStore();
        input.setCountry("mx");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));
        when(storeRepo.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        Store saved = storeService.createStore(input, 1L);

        assertThat(saved.getCountry()).isEqualTo("MX");
    }

    @Test
    @DisplayName("createStore: well-formed but unassigned country XX is rejected")
    void createStoreUnassignedCountryThrows() {
        Store input = validStore();
        input.setCountry("XX");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));

        assertThatThrownBy(() -> storeService.createStore(input, 1L))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessageContaining("ISO 3166-1 alpha-2");

        verify(storeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createStore: missing timezone is rejected before ZoneId parsing")
    void createStoreMissingTimezoneThrows() {
        Store input = validStore();
        input.setTimezone(null);
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));

        assertThatThrownBy(() -> storeService.createStore(input, 1L))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessage("Timezone is required");
    }

    @Test
    @DisplayName("createStore: UTC-offset timezone is rejected; must be an IANA identifier")
    void createStoreOffsetTimezoneThrows() {
        Store input = validStore();
        input.setTimezone("-08:00");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));

        assertThatThrownBy(() -> storeService.createStore(input, 1L))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessageContaining("valid IANA identifier");

        verify(storeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createStore: unknown region timezone is rejected")
    void createStoreUnknownRegionTimezoneThrows() {
        Store input = validStore();
        input.setTimezone("Mars/Olympus_Mons");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));

        assertThatThrownBy(() -> storeService.createStore(input, 1L))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessageContaining("valid IANA identifier");
    }

    @Test
    @DisplayName("createStore: Mexico_City IANA zone is accepted")
    void createStoreAmericaMexicoCityAccepted() {
        Store input = validStore();
        input.setTimezone("America/Mexico_City");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));
        when(storeRepo.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        Store saved = storeService.createStore(input, 1L);

        assertThat(saved.getTimezone()).isEqualTo("America/Mexico_City");
    }

    @Test
    @DisplayName("createStore: duplicate store name across stores is allowed (name is not unique)")
    void createStoreDuplicateNameIsAllowed() {
        Store input = validStore();
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));
        when(storeRepo.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        Store saved = storeService.createStore(input, 1L);

        assertThat(saved.getName()).isEqualTo("Downtown");
        // No repository method is consulted to enforce name uniqueness.
        verify(storeRepo).save(input);
    }

    @Test
    @DisplayName("createStore: duplicate store number within the organization is rejected")
    void createStoreDuplicateStoreNumberThrows() {
        Store input = validStore();
        input.setStoreNumber("0042");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));
        when(storeRepo.existsByOrganizationIdAndStoreNumber(1L, "0042")).thenReturn(true);

        assertThatThrownBy(() -> storeService.createStore(input, 1L))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessageContaining("Store number already exists");

        verify(storeRepo, never()).save(any());
    }

    @Test
    @DisplayName("createStore: null store number bypasses the uniqueness check")
    void createStoreNullStoreNumberSkipsUniquenessCheck() {
        Store input = validStore();
        input.setStoreNumber(null);
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));
        when(storeRepo.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        Store saved = storeService.createStore(input, 1L);

        assertThat(saved.getStoreNumber()).isNull();
        verify(storeRepo, never()).existsByOrganizationIdAndStoreNumber(any(), any());
    }

    @Test
    @DisplayName("createStore: blank store number is normalized to null before persistence")
    void createStoreBlankStoreNumberBecomesNull() {
        Store input = validStore();
        input.setStoreNumber("   ");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));
        when(storeRepo.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        Store saved = storeService.createStore(input, 1L);

        assertThat(saved.getStoreNumber()).isNull();
        verify(storeRepo, never()).existsByOrganizationIdAndStoreNumber(any(), any());
    }

    // --- updateStore ---

    @Test
    @DisplayName("updateStore: every mutable field is replaced, including address and contact")
    void updateStoreAppliesChanges() {
        Organization globex = new Organization("Globex");
        Store existing = validStore();
        existing.setOrganization(acme());
        Store replacement = new Store(null, "Airport", "MX", "CDMX", "Mexico City",
                "Av. Reforma 1", "06000", "America/Mexico_City");
        replacement.setStoreNumber("MX-01");
        replacement.setPhoneNumber("+52 55 5555 5555");
        replacement.setEmail("airport@example.mx");
        replacement.setActive(false);
        when(organizationRepo.findById(2L)).thenReturn(Optional.of(globex));
        when(storeRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(storeRepo.existsByOrganizationIdAndStoreNumberAndIdNot(2L, "MX-01", 1L)).thenReturn(false);
        when(storeRepo.save(any(Store.class))).thenAnswer(inv -> inv.getArgument(0));

        Store updated = storeService.updateStore(1L, replacement, 2L);

        assertThat(updated.getName()).isEqualTo("Airport");
        assertThat(updated.getCountry()).isEqualTo("MX");
        assertThat(updated.getStateProvince()).isEqualTo("CDMX");
        assertThat(updated.getCity()).isEqualTo("Mexico City");
        assertThat(updated.getTimezone()).isEqualTo("America/Mexico_City");
        assertThat(updated.getStoreNumber()).isEqualTo("MX-01");
        assertThat(updated.getEmail()).isEqualTo("airport@example.mx");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getOrganization()).isSameAs(globex);
    }

    @Test
    @DisplayName("updateStore: missing store ID throws StoreNotFoundException")
    void updateStoreMissingThrows() {
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));
        when(storeRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.updateStore(99L, validStore(), 1L))
                .isInstanceOf(StoreNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("updateStore: re-parenting to an unknown organization is rejected")
    void updateStoreUnknownOrganizationThrows() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> storeService.updateStore(1L, validStore(), 99L))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");

        verify(storeRepo, never()).findById(any());
    }

    @Test
    @DisplayName("updateStore: store number conflicting with another store in the target org is rejected")
    void updateStoreStoreNumberConflictThrows() {
        Store existing = validStore();
        existing.setOrganization(acme());
        Store replacement = validStore();
        replacement.setStoreNumber("0042");
        Organization acme = acme();
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme));
        when(storeRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(storeRepo.existsByOrganizationIdAndStoreNumberAndIdNot(eq(1L), eq("0042"), eq(1L)))
                .thenReturn(true);

        assertThatThrownBy(() -> storeService.updateStore(1L, replacement, 1L))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessageContaining("Store number already exists");

        verify(storeRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateStore: invalid timezone on the replacement is rejected before persistence")
    void updateStoreInvalidTimezoneThrows() {
        Store replacement = validStore();
        replacement.setTimezone("Not/AZone");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(acme()));

        assertThatThrownBy(() -> storeService.updateStore(1L, replacement, 1L))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessageContaining("valid IANA identifier");

        verify(storeRepo, never()).findById(any());
    }

    // --- deleteStore / getAllStores / getStoreById ---

    @Test
    @DisplayName("deleteStore: delegates to the repository")
    void deleteStoreDelegatesToRepository() {
        storeService.deleteStore(1L);

        verify(storeRepo).deleteById(1L);
    }

    @Test
    @DisplayName("getAllStores: returns every store")
    void getAllStoresReturnsAll() {
        Organization acme = acme();
        Store downtown = validStore();
        downtown.setOrganization(acme);
        Store airport = validStore();
        airport.setName("Airport");
        airport.setOrganization(acme);
        when(storeRepo.findAll()).thenReturn(List.of(downtown, airport));

        List<Store> all = storeService.getAllStores();

        assertThat(all).hasSize(2)
                .extracting(Store::getName)
                .containsExactly("Downtown", "Airport");
    }

    @Test
    @DisplayName("getStoreById: null ID throws IllegalArgumentException")
    void getStoreByIdNullThrows() {
        assertThatThrownBy(() -> storeService.getStoreById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ID cannot be null");
    }

    @Test
    @DisplayName("getStoreById: delegates to the repository when the ID is present")
    void getStoreByIdDelegates() {
        Store existing = validStore();
        when(storeRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(storeService.getStoreById(1L)).contains(existing);
    }
}
