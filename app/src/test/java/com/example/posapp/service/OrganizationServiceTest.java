package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.example.posapp.repository.OrganizationRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Unit tests for the {@link OrganizationService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service in
 * isolation: name validation, duplicate-name rejection, the delete guard
 * that keeps an organization alive while it still owns stores, and the
 * store listing sub-resource. No Spring context or database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepo;

    @Mock
    private StoreRepository storeRepo;

    @InjectMocks
    private OrganizationService organizationService;

    private static Organization org(String name) {
        return new Organization(name);
    }

    /**
     * Build a fully-populated store so it satisfies the required address
     * and timezone fields added to the tenancy foundation.
     */
    private static Store sampleStore(Organization owner, String name) {
        return new Store(owner, name, "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
    }

    // --- createOrganization ---

    @Test
    @DisplayName("createOrganization: valid organization is saved and returned")
    void createOrganizationValidIsSaved() {
        Organization input = org("Acme Restaurants");
        when(organizationRepo.existsByName("Acme Restaurants")).thenReturn(false);
        when(organizationRepo.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        Organization saved = organizationService.createOrganization(input);

        assertThat(saved.getName()).isEqualTo("Acme Restaurants");
        assertThat(saved.isActive()).isTrue();
        verify(organizationRepo).save(input);
    }

    @Test
    @DisplayName("createOrganization: blank name is rejected")
    void createOrganizationBlankNameThrows() {
        assertThatThrownBy(() -> organizationService.createOrganization(org("   ")))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessage("Name must be provided");

        verify(organizationRepo, never()).save(any());
    }

    @Test
    @DisplayName("createOrganization: missing name is rejected")
    void createOrganizationNullNameThrows() {
        assertThatThrownBy(() -> organizationService.createOrganization(org(null)))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessage("Name must be provided");

        verify(organizationRepo, never()).save(any());
    }

    @Test
    @DisplayName("createOrganization: duplicate name is rejected at the service layer")
    void createOrganizationDuplicateNameThrows() {
        when(organizationRepo.existsByName("Acme Restaurants")).thenReturn(true);

        assertThatThrownBy(() -> organizationService.createOrganization(org("Acme Restaurants")))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessageContaining("Organization name already exists");

        verify(organizationRepo, never()).save(any());
    }

    // --- updateOrganization ---

    @Test
    @DisplayName("updateOrganization: name and active flag are applied to the existing row")
    void updateOrganizationAppliesChanges() {
        Organization existing = org("Old Name");
        Organization replacement = org("New Name");
        replacement.setActive(false);
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(organizationRepo.existsByNameAndIdNot("New Name", 1L)).thenReturn(false);
        when(organizationRepo.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        Organization updated = organizationService.updateOrganization(1L, replacement);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    @DisplayName("updateOrganization: missing ID throws OrganizationNotFoundException")
    void updateOrganizationMissingThrows() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.updateOrganization(99L, org("Whatever")))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("updateOrganization: name used by another organization is rejected")
    void updateOrganizationDuplicateNameThrows() {
        Organization existing = org("Old");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(organizationRepo.existsByNameAndIdNot("Taken", 1L)).thenReturn(true);

        assertThatThrownBy(() -> organizationService.updateOrganization(1L, org("Taken")))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessageContaining("Organization name already exists");

        verify(organizationRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateOrganization: blank name is rejected")
    void updateOrganizationBlankNameThrows() {
        assertThatThrownBy(() -> organizationService.updateOrganization(1L, org("  ")))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessage("Name must be provided");
    }

    // --- deleteOrganization ---

    @Test
    @DisplayName("deleteOrganization: repository deleteById is called when no stores remain")
    void deleteOrganizationWithNoStoresSucceeds() {
        when(organizationRepo.existsById(1L)).thenReturn(true);
        when(storeRepo.countByOrganizationId(1L)).thenReturn(0L);

        organizationService.deleteOrganization(1L);

        verify(organizationRepo).deleteById(1L);
    }

    @Test
    @DisplayName("deleteOrganization: rejects deletion when the organization still owns stores")
    void deleteOrganizationWithStoresThrows() {
        when(organizationRepo.existsById(2L)).thenReturn(true);
        when(storeRepo.countByOrganizationId(2L)).thenReturn(3L);

        assertThatThrownBy(() -> organizationService.deleteOrganization(2L))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessageContaining("still owning stores");

        verify(organizationRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteOrganization: missing ID throws OrganizationNotFoundException")
    void deleteOrganizationMissingThrows() {
        when(organizationRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> organizationService.deleteOrganization(99L))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");

        verify(organizationRepo, never()).deleteById(any());
    }

    // --- getAll / getById ---

    @Test
    @DisplayName("getAllOrganizations: returns every organization")
    void getAllOrganizationsReturnsAll() {
        when(organizationRepo.findAll()).thenReturn(List.of(org("Acme"), org("Globex")));

        List<Organization> all = organizationService.getAllOrganizations();

        assertThat(all).hasSize(2)
                .extracting(Organization::getName)
                .containsExactly("Acme", "Globex");
    }

    @Test
    @DisplayName("getOrganizationById: null ID throws IllegalArgumentException")
    void getOrganizationByIdNullThrows() {
        assertThatThrownBy(() -> organizationService.getOrganizationById(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ID cannot be null");
    }

    @Test
    @DisplayName("getOrganizationById: delegates to the repository when the ID is present")
    void getOrganizationByIdDelegates() {
        Organization existing = org("Acme");
        when(organizationRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(organizationService.getOrganizationById(1L)).contains(existing);
    }

    // --- listStores ---

    @Test
    @DisplayName("listStores: returns stores from the repository for an existing organization")
    void listStoresReturnsStoreList() {
        Organization organization = org("Acme");
        Store downtown = sampleStore(organization, "Downtown");
        Store airport = sampleStore(organization, "Airport");
        when(organizationRepo.existsById(1L)).thenReturn(true);
        when(storeRepo.findByOrganizationId(1L)).thenReturn(List.of(downtown, airport));

        List<Store> result = organizationService.listStores(1L);

        assertThat(result).hasSize(2)
                .extracting(Store::getName)
                .containsExactly("Downtown", "Airport");
    }

    @Test
    @DisplayName("listStores: throws OrganizationNotFoundException when the organization is missing")
    void listStoresMissingOrganizationThrows() {
        when(organizationRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> organizationService.listStores(99L))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");

        verify(storeRepo, never()).findByOrganizationId(any());
    }
}
