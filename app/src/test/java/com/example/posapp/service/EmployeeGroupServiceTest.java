package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import com.example.posapp.exception.EmployeeGroupNotFoundException;
import com.example.posapp.exception.EmployeeGroupValidationException;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.repository.EmployeeGroupMembershipRepository;
import com.example.posapp.repository.EmployeeGroupRepository;
import com.example.posapp.repository.OrganizationRepository;

/**
 * Unit tests for the {@link EmployeeGroupService} business rules.
 * <p>
 * Repositories are mocked, so these tests exercise the service in
 * isolation: names are required, the owning organization must be the
 * caller's brand, a parent must exist in that brand, self-parenting and
 * indirect cycles are rejected, a group with child groups or members cannot
 * be deleted, and the child listing requires a parent inside the brand.
 * Entities cannot be given IDs directly, so tests that depend on distinct
 * IDs (ancestor cycles) stub Mockito mocks instead of persisted rows. No
 * Spring context or database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeGroupServiceTest {

    /** The caller's brand, and the only organization these tests may name. */
    private static final long BRAND = 1L;

    /** A second brand the caller must never reach. */
    private static final long OTHER_BRAND = 2L;

    @Mock
    private EmployeeGroupRepository employeeGroupRepo;

    @Mock
    private OrganizationRepository organizationRepo;

    @Mock
    private EmployeeGroupMembershipRepository membershipRepo;

    @InjectMocks
    private EmployeeGroupService employeeGroupService;

    private static Organization org(String name) {
        return new Organization(name);
    }

    private static EmployeeGroup group(Organization owner, String name) {
        EmployeeGroup group = new EmployeeGroup(name);
        group.setOrganization(owner);
        return group;
    }

    // --- createEmployeeGroup ---

    @Test
    @DisplayName("createEmployeeGroup: valid root group is saved and returned")
    void createEmployeeGroupValidRootIsSaved() {
        Organization owner = org("Acme Restaurants");
        EmployeeGroup input = new EmployeeGroup("Front of House");
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(owner));
        when(employeeGroupRepo.save(any(EmployeeGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeGroup saved = employeeGroupService.createEmployeeGroup(input, BRAND, null, BRAND);

        assertThat(saved.getName()).isEqualTo("Front of House");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getOrganization()).isSameAs(owner);
        assertThat(saved.getParent()).isNull();
        verify(employeeGroupRepo).save(input);
    }

    @Test
    @DisplayName("createEmployeeGroup: explicitly inactive group is accepted and stays inactive")
    void createEmployeeGroupInactiveIsAccepted() {
        Organization owner = org("Acme Restaurants");
        EmployeeGroup input = new EmployeeGroup("Retired Team");
        input.setActive(false);
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(owner));
        when(employeeGroupRepo.save(any(EmployeeGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeGroup saved = employeeGroupService.createEmployeeGroup(input, BRAND, null, BRAND);

        assertThat(saved.isActive()).isFalse();
    }

    @Test
    @DisplayName("createEmployeeGroup: group below a parent of the same brand is saved")
    void createEmployeeGroupWithParentIsSaved() {
        Organization owner = org("Acme Restaurants");
        EmployeeGroup parent = group(owner, "Front of House");
        EmployeeGroup input = new EmployeeGroup("Cashiers");
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(owner));
        when(employeeGroupRepo.findByIdAndOrganizationId(2L, BRAND)).thenReturn(Optional.of(parent));
        when(employeeGroupRepo.save(any(EmployeeGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeGroup saved = employeeGroupService.createEmployeeGroup(input, BRAND, 2L, BRAND);

        assertThat(saved.getParent()).isSameAs(parent);
        assertThat(saved.getOrganization()).isSameAs(owner);
    }

    @Test
    @DisplayName("createEmployeeGroup: blank name is rejected and nothing is saved")
    void createEmployeeGroupBlankNameIsRejected() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme Restaurants")));

        assertThatThrownBy(() -> employeeGroupService.createEmployeeGroup(
                new EmployeeGroup("   "), BRAND, null, BRAND))
                .isInstanceOf(EmployeeGroupValidationException.class)
                .hasMessage("Name must be provided");

        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("createEmployeeGroup: null name is rejected and nothing is saved")
    void createEmployeeGroupNullNameIsRejected() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme Restaurants")));

        assertThatThrownBy(() -> employeeGroupService.createEmployeeGroup(
                new EmployeeGroup(null), BRAND, null, BRAND))
                .isInstanceOf(EmployeeGroupValidationException.class)
                .hasMessage("Name must be provided");

        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("createEmployeeGroup: null organization ID throws OrganizationNotFoundException")
    void createEmployeeGroupNullOrganizationThrows() {
        assertThatThrownBy(() -> employeeGroupService.createEmployeeGroup(
                new EmployeeGroup("Front of House"), null, null, BRAND))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("Organization not found");

        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("createEmployeeGroup: missing organization throws OrganizationNotFoundException")
    void createEmployeeGroupMissingOrganizationThrows() {
        when(organizationRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeGroupService.createEmployeeGroup(
                new EmployeeGroup("Front of House"), 99L, null, 99L))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("createEmployeeGroup: naming another brand's organization is answered as not found")
    void createEmployeeGroupOutsideBrandIsNotFound() {
        assertThatThrownBy(() -> employeeGroupService.createEmployeeGroup(
                new EmployeeGroup("Front of House"), OTHER_BRAND, null, BRAND))
                .isInstanceOf(OrganizationNotFoundException.class)
                .hasMessageContaining("2");

        verify(organizationRepo, never()).findById(any());
        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("createEmployeeGroup: missing parent throws EmployeeGroupNotFoundException")
    void createEmployeeGroupMissingParentThrows() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme Restaurants")));
        when(employeeGroupRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeGroupService.createEmployeeGroup(
                new EmployeeGroup("Cashiers"), BRAND, 99L, BRAND))
                .isInstanceOf(EmployeeGroupNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("createEmployeeGroup: a parent of another brand is answered as a missing parent")
    void createEmployeeGroupOutsideBrandParentIsMissing() {
        when(organizationRepo.findById(BRAND)).thenReturn(Optional.of(org("Acme Restaurants")));

        assertThatThrownBy(() -> employeeGroupService.createEmployeeGroup(
                new EmployeeGroup("Cashiers"), BRAND, OTHER_BRAND, BRAND))
                .isInstanceOf(EmployeeGroupNotFoundException.class)
                .hasMessageContaining("2");

        verify(employeeGroupRepo).findByIdAndOrganizationId(OTHER_BRAND, BRAND);
        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    // --- updateEmployeeGroup ---

    @Test
    @DisplayName("updateEmployeeGroup: name and active flag are applied to the existing row")
    void updateEmployeeGroupAppliesChanges() {
        EmployeeGroup existing = new EmployeeGroup("Front of House");
        EmployeeGroup changes = new EmployeeGroup("Front of House Team");
        changes.setActive(false);
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND)).thenReturn(Optional.of(existing));
        when(employeeGroupRepo.save(any(EmployeeGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeGroup updated = employeeGroupService.updateEmployeeGroup(1L, changes, null, BRAND);

        assertThat(updated.getName()).isEqualTo("Front of House Team");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getParent()).isNull();
        verify(employeeGroupRepo).save(existing);
    }

    @Test
    @DisplayName("updateEmployeeGroup: re-parenting to a group of the same brand is applied")
    void updateEmployeeGroupReparentsWithinBrand() {
        Organization owner = org("Acme Restaurants");
        EmployeeGroup existing = group(owner, "Cashiers");
        EmployeeGroup newParent = group(owner, "Front of House");
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND)).thenReturn(Optional.of(existing));
        when(employeeGroupRepo.findByIdAndOrganizationId(2L, BRAND)).thenReturn(Optional.of(newParent));
        when(employeeGroupRepo.save(any(EmployeeGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        EmployeeGroup updated = employeeGroupService.updateEmployeeGroup(
                1L, new EmployeeGroup("Cashiers"), 2L, BRAND);

        assertThat(updated.getParent()).isSameAs(newParent);
        verify(employeeGroupRepo).save(existing);
    }

    @Test
    @DisplayName("updateEmployeeGroup: assigning the group as its own parent is rejected")
    void updateEmployeeGroupSelfParentThrows() {
        EmployeeGroup existing = new EmployeeGroup("Front of House");
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> employeeGroupService.updateEmployeeGroup(
                1L, new EmployeeGroup("Front of House"), 1L, BRAND))
                .isInstanceOf(EmployeeGroupValidationException.class)
                .hasMessageContaining("cannot be its own parent");

        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("updateEmployeeGroup: indirect cycle is rejected when the requested parent is a descendant")
    void updateEmployeeGroupIndirectCycleThrows() {
        Organization owner = mock(Organization.class);
        EmployeeGroup existing = mock(EmployeeGroup.class);
        when(existing.getId()).thenReturn(1L);
        EmployeeGroup directChild = mock(EmployeeGroup.class);
        when(directChild.getId()).thenReturn(2L);
        when(directChild.getParent()).thenReturn(existing);
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND)).thenReturn(Optional.of(existing));
        when(employeeGroupRepo.findByIdAndOrganizationId(2L, BRAND)).thenReturn(Optional.of(directChild));

        assertThatThrownBy(() -> employeeGroupService.updateEmployeeGroup(
                1L, new EmployeeGroup("Front of House"), 2L, BRAND))
                .isInstanceOf(EmployeeGroupValidationException.class)
                .hasMessageContaining("Circular employee group hierarchy");

        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("updateEmployeeGroup: a parent of another brand is answered as a missing parent")
    void updateEmployeeGroupOutsideBrandParentIsMissing() {
        EmployeeGroup existing = new EmployeeGroup("Cashiers");
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> employeeGroupService.updateEmployeeGroup(
                1L, new EmployeeGroup("Cashiers"), OTHER_BRAND, BRAND))
                .isInstanceOf(EmployeeGroupNotFoundException.class)
                .hasMessageContaining("2");

        verify(employeeGroupRepo).findByIdAndOrganizationId(OTHER_BRAND, BRAND);
        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("updateEmployeeGroup: blank name is rejected before any lookup")
    void updateEmployeeGroupBlankNameIsRejected() {
        assertThatThrownBy(() -> employeeGroupService.updateEmployeeGroup(
                1L, new EmployeeGroup("  "), null, BRAND))
                .isInstanceOf(EmployeeGroupValidationException.class)
                .hasMessage("Name must be provided");

        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("updateEmployeeGroup: throws EmployeeGroupNotFoundException when the group does not exist")
    void updateEmployeeGroupMissingThrows() {
        when(employeeGroupRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeGroupService.updateEmployeeGroup(
                99L, new EmployeeGroup("Ghost"), null, BRAND))
                .isInstanceOf(EmployeeGroupNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("updateEmployeeGroup: a group of another brand is answered as missing")
    void updateEmployeeGroupOutsideBrandIsMissing() {
        assertThatThrownBy(() -> employeeGroupService.updateEmployeeGroup(
                1L, new EmployeeGroup("Ghost"), null, OTHER_BRAND))
                .isInstanceOf(EmployeeGroupNotFoundException.class)
                .hasMessageContaining("1");

        verify(employeeGroupRepo).findByIdAndOrganizationId(1L, OTHER_BRAND);
        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    @Test
    @DisplayName("updateEmployeeGroup: missing parent throws EmployeeGroupNotFoundException")
    void updateEmployeeGroupMissingParentThrows() {
        EmployeeGroup existing = new EmployeeGroup("Cashiers");
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND)).thenReturn(Optional.of(existing));
        when(employeeGroupRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeGroupService.updateEmployeeGroup(
                1L, new EmployeeGroup("Cashiers"), 99L, BRAND))
                .isInstanceOf(EmployeeGroupNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeGroupRepo, never()).save(any(EmployeeGroup.class));
    }

    // --- deleteEmployeeGroup ---

    @Test
    @DisplayName("deleteEmployeeGroup: leaf group is deleted")
    void deleteEmployeeGroupLeafSucceeds() {
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND))
                .thenReturn(Optional.of(new EmployeeGroup("Cashiers")));
        when(employeeGroupRepo.countByParentId(1L)).thenReturn(0L);

        employeeGroupService.deleteEmployeeGroup(1L, BRAND);

        verify(employeeGroupRepo).deleteById(1L);
    }

    @Test
    @DisplayName("deleteEmployeeGroup: group with child groups is rejected and nothing is deleted")
    void deleteEmployeeGroupWithChildrenIsRejected() {
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND))
                .thenReturn(Optional.of(new EmployeeGroup("Front of House")));
        when(employeeGroupRepo.countByParentId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> employeeGroupService.deleteEmployeeGroup(1L, BRAND))
                .isInstanceOf(EmployeeGroupValidationException.class)
                .hasMessageContaining("still having child groups");

        verify(employeeGroupRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteEmployeeGroup: throws EmployeeGroupNotFoundException for missing group")
    void deleteEmployeeGroupMissingThrows() {
        when(employeeGroupRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeGroupService.deleteEmployeeGroup(99L, BRAND))
                .isInstanceOf(EmployeeGroupNotFoundException.class);

        verify(employeeGroupRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteEmployeeGroup: a group of another brand is answered as missing")
    void deleteEmployeeGroupOutsideBrandIsMissing() {
        assertThatThrownBy(() -> employeeGroupService.deleteEmployeeGroup(1L, OTHER_BRAND))
                .isInstanceOf(EmployeeGroupNotFoundException.class);

        verify(employeeGroupRepo).findByIdAndOrganizationId(1L, OTHER_BRAND);
        verify(employeeGroupRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteEmployeeGroup: group with employee members is rejected and nothing is deleted")
    void deleteEmployeeGroupWithMembersIsRejected() {
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND))
                .thenReturn(Optional.of(new EmployeeGroup("Front of House")));
        when(employeeGroupRepo.countByParentId(1L)).thenReturn(0L);
        when(membershipRepo.countByEmployeeGroupId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> employeeGroupService.deleteEmployeeGroup(1L, BRAND))
                .isInstanceOf(EmployeeGroupValidationException.class)
                .hasMessageContaining("still having employee members");

        verify(employeeGroupRepo, never()).deleteById(any());
    }

    // --- getAll / getById ---

    @Test
    @DisplayName("getAllEmployeeGroups: asks only for the caller's brand")
    void getAllEmployeeGroupsReturnsBrandsGroups() {
        when(employeeGroupRepo.findByOrganizationId(BRAND)).thenReturn(List.of(
                new EmployeeGroup("Front of House"),
                new EmployeeGroup("Back of House")));

        List<EmployeeGroup> groups = employeeGroupService.getAllEmployeeGroups(BRAND);

        assertThat(groups).hasSize(2)
                .extracting(EmployeeGroup::getName)
                .containsExactly("Front of House", "Back of House");
        verify(employeeGroupRepo, never()).findAll();
    }

    @Test
    @DisplayName("getEmployeeGroupById: null ID throws IllegalArgumentException")
    void getEmployeeGroupByIdNullThrows() {
        assertThatThrownBy(() -> employeeGroupService.getEmployeeGroupById(null, BRAND))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ID cannot be null");
    }

    @Test
    @DisplayName("getEmployeeGroupById: delegates to the scoped repository when the ID is present")
    void getEmployeeGroupByIdDelegates() {
        EmployeeGroup existing = new EmployeeGroup("Front of House");
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND)).thenReturn(Optional.of(existing));

        assertThat(employeeGroupService.getEmployeeGroupById(1L, BRAND)).contains(existing);
    }

    @Test
    @DisplayName("getEmployeeGroupById: a group of another brand is not visible")
    void getEmployeeGroupByIdOutsideBrandIsEmpty() {
        assertThat(employeeGroupService.getEmployeeGroupById(1L, OTHER_BRAND)).isEmpty();

        verify(employeeGroupRepo).findByIdAndOrganizationId(1L, OTHER_BRAND);
    }

    // --- listChildGroups ---

    @Test
    @DisplayName("listChildGroups: returns the direct children of an existing group")
    void listChildGroupsReturnsChildren() {
        when(employeeGroupRepo.findByIdAndOrganizationId(1L, BRAND))
                .thenReturn(Optional.of(new EmployeeGroup("Front of House")));
        when(employeeGroupRepo.findByParentId(1L)).thenReturn(List.of(
                new EmployeeGroup("Cashiers"),
                new EmployeeGroup("Hosts")));

        List<EmployeeGroup> children = employeeGroupService.listChildGroups(1L, BRAND);

        assertThat(children).hasSize(2)
                .extracting(EmployeeGroup::getName)
                .containsExactly("Cashiers", "Hosts");
    }

    @Test
    @DisplayName("listChildGroups: throws EmployeeGroupNotFoundException for a missing group")
    void listChildGroupsMissingThrows() {
        when(employeeGroupRepo.findByIdAndOrganizationId(99L, BRAND)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeGroupService.listChildGroups(99L, BRAND))
                .isInstanceOf(EmployeeGroupNotFoundException.class)
                .hasMessageContaining("99");

        verify(employeeGroupRepo, never()).findByParentId(any());
    }
}
