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

import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.BrandScopeException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.exception.OrganizationValidationException;
import com.example.posapp.repository.EmployeeGroupRepository;
import com.example.posapp.repository.OrganizationRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Unit tests for the {@link OrganizationService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service in
 * isolation: name validation and name uniqueness on update, the reads
 * confined to the caller's brand, and the store and root employee group
 * listing sub-resources.
 * </p>
 * <p>
 * Creating or destroying a brand is outside the reach of a brand
 * administrator, so both lifecycle operations are asserted to refuse with
 * {@link BrandScopeException} before touching a repository. The
 * name-uniqueness check on create and the delete guards that keep an
 * organization alive while it still owns stores, employee groups, or
 * employees are suspended along with those operations and are documented on
 * the service; a platform-level role will own them.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    /** The caller's brand, and the only organization these tests may name. */
    private static final long BRAND = 1L;

    /** A second brand the caller must never reach. */
    private static final long OTHER_BRAND = 2L;

    @Mock
    private OrganizationRepository organizationRepo;

    @Mock
    private StoreRepository storeRepo;

    @Mock
    private EmployeeGroupRepository employeeGroupRepo;

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

    /**
     * Build a root employee group owned by the given organization.
     */
    private static EmployeeGroup group(Organization owner, String name) {
        EmployeeGroup group = new EmployeeGroup(name);
        group.setOrganization(owner);
        return group;
    }

    // --- createOrganization ---

    @Test
    @DisplayName("createOrganization: a brand administrator cannot create a second tenant")
    void createOrganizationAlwaysRefuses() {
        Organization input = org("Acme Restaurants");

        assertThatThrownBy(() -> organizationService.createOrganization(input, BRAND))
                .isInstanceOf(BrandScopeException.class)
                .hasMessageContaining("creating an organization");

        verify(organizationRepo, never()).save(any());
        verify(organizationRepo, never()).existsByName(any());
    }

    @Test
    @DisplayName("createOrganization: the refusal does not depend on the payload or the brand")
    void createOrganizationRefusesRegardlessOfInput() {
        assertThatThrownBy(() -> organizationService.createOrganization(org(null), OTHER_BRAND))
                .isInstanceOf(BrandScopeException.class);
        assertThatThrownBy(() -> organizationService.createOrganization(null, BRAND))
                .isInstanceOf(BrandScopeException.class);

        verify(organizationRepo, never()).save(any());
    }

    // --- updateOrganization ---

    @Test
    @DisplayName("updateOrganization: name and active flag are applied to the existing row")
    void updateOrganizationAppliesChanges() {
        Organization existing = org("Old Name");
        Organization replacement = org("New Name");
        replacement.setActive(false);
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(existing));
        when(organizationRepo.existsByNameAndIdNot("New Name", BRAND)).thenReturn(false);
        when(organizationRepo.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

        Organization updated = organizationService.updateOrganization(BRAND, replacement, BRAND);

        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    @DisplayName("updateOrganization: missing ID throws OrganizationNotFoundException")
    void updateOrganizationMissingThrows() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.updateOrganization(99L, org("Whatever"), 99L))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("updateOrganization: another brand's organization is answered as not found")
    void updateOrganizationOutsideBrandIsNotFound() {
        assertThatThrownBy(() -> organizationService.updateOrganization(OTHER_BRAND, org("Globex"), BRAND))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("2");

        verify(organizationRepo, never()).findById(any());
        verify(organizationRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateOrganization: name used by another organization is rejected")
    void updateOrganizationDuplicateNameThrows() {
        Organization existing = org("Old");
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(existing));
        when(organizationRepo.existsByNameAndIdNot("Taken", BRAND)).thenReturn(true);

        assertThatThrownBy(() -> organizationService.updateOrganization(BRAND, org("Taken"), BRAND))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessageContaining("Organization name already exists");

        verify(organizationRepo, never()).save(any());
    }

    @Test
    @DisplayName("updateOrganization: blank name is rejected")
    void updateOrganizationBlankNameThrows() {
        assertThatThrownBy(() -> organizationService.updateOrganization(BRAND, org("  "), BRAND))
                .isInstanceOf(OrganizationValidationException.class)
                .hasMessage("Name must be provided");

        verify(organizationRepo, never()).findById(any());
    }

    // --- deleteOrganization ---

    @Test
    @DisplayName("deleteOrganization: a brand administrator cannot destroy the tenant")
    void deleteOrganizationAlwaysRefuses() {
        assertThatThrownBy(() -> organizationService.deleteOrganization(BRAND, BRAND))
                .isInstanceOf(BrandScopeException.class)
                .hasMessageContaining("deleting an organization");

        verify(organizationRepo, never()).deleteById(any());
        verify(storeRepo, never()).countByOrganizationId(any());
    }

    // --- getAll / getById ---

    @Test
    @DisplayName("getAllOrganizations: the back office sees its own brand only")
    void getAllOrganizationsReturnsOwnBrand() {
        Organization brand = org("Acme");
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(brand));

        List<Organization> all = organizationService.getAllOrganizations(BRAND);

        assertThat(all).containsExactly(brand);
        verify(organizationRepo, never()).findAll();
    }

    @Test
    @DisplayName("getAllOrganizations: an unreadable brand yields an empty list rather than failing")
    void getAllOrganizationsEmptyWhenBrandVanished() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.empty());

        assertThat(organizationService.getAllOrganizations(BRAND)).isEmpty();
    }

    @Test
    @DisplayName("getOrganizationById: null ID throws IllegalArgumentException")
    void getOrganizationByIdNullThrows() {
        assertThatThrownBy(() -> organizationService.getOrganizationById(null, BRAND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ID cannot be null");
    }

    @Test
    @DisplayName("getOrganizationById: delegates to the repository when the ID is the brand")
    void getOrganizationByIdDelegates() {
        Organization existing = org("Acme");
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(existing));

        assertThat(organizationService.getOrganizationById(BRAND, BRAND)).contains(existing);
    }

    @Test
    @DisplayName("getOrganizationById: another brand's organization is not visible")
    void getOrganizationByIdOutsideBrandIsEmpty() {
        assertThat(organizationService.getOrganizationById(OTHER_BRAND, BRAND)).isEmpty();

        verify(organizationRepo, never()).findById(any());
    }

    // --- listStores ---

    @Test
    @DisplayName("listStores: returns stores from the repository for the caller's brand")
    void listStoresReturnsStoreList() {
        Organization organization = org("Acme");
        Store downtown = sampleStore(organization, "Downtown");
        Store airport = sampleStore(organization, "Airport");
        when(storeRepo.findByOrganizationId(BRAND)).thenReturn(List.of(downtown, airport));

        List<Store> result = organizationService.listStores(BRAND, BRAND);

        assertThat(result).hasSize(2)
                .extracting(Store::getName)
                .containsExactly("Downtown", "Airport");
    }

    @Test
    @DisplayName("listStores: another brand's organization is answered as not found")
    void listStoresOutsideBrandThrows() {
        assertThatThrownBy(() -> organizationService.listStores(OTHER_BRAND, BRAND))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("2");

        verify(storeRepo, never()).findByOrganizationId(any());
    }

    // --- listRootEmployeeGroups ---

    @Test
    @DisplayName("listRootEmployeeGroups: returns the root groups for the caller's brand")
    void listRootEmployeeGroupsReturnsRoots() {
        Organization organization = org("Acme");
        when(employeeGroupRepo.findByOrganizationIdAndParentIsNull(BRAND)).thenReturn(List.of(
                group(organization, "Front of House"),
                group(organization, "Back of House")));

        List<EmployeeGroup> roots = organizationService.listRootEmployeeGroups(BRAND, BRAND);

        assertThat(roots).hasSize(2)
                .extracting(EmployeeGroup::getName)
                .containsExactly("Front of House", "Back of House");
    }

    @Test
    @DisplayName("listRootEmployeeGroups: another brand's organization is answered as not found")
    void listRootEmployeeGroupsOutsideBrandThrows() {
        assertThatThrownBy(() -> organizationService.listRootEmployeeGroups(OTHER_BRAND, BRAND))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("2");

        verify(employeeGroupRepo, never()).findByOrganizationIdAndParentIsNull(any());
    }
}
