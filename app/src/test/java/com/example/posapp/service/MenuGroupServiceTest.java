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

import com.example.posapp.entity.MenuGroup;
import com.example.posapp.exception.MenuGroupNotFoundException;
import com.example.posapp.exception.MenuValidationException;
import com.example.posapp.repository.MenuGroupAssignmentRepository;
import com.example.posapp.repository.MenuGroupRepository;

/**
 * Unit tests for the {@link MenuGroupService} business rules.
 * <p>
 * Menu groups are reusable across menus. These tests exercise name
 * validation, duplicate-name rejection, and the guard that blocks deleting
 * a group still assigned to at least one menu. No Spring context or
 * database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MenuGroupServiceTest {

    @Mock
    private MenuGroupRepository menuGroupRepo;

    @Mock
    private MenuGroupAssignmentRepository assignmentRepo;

    @InjectMocks
    private MenuGroupService menuGroupService;

    // --- createMenuGroup ---

    @Test
    @DisplayName("createMenuGroup: valid group is saved and returned")
    void createMenuGroupValidIsSaved() {
        MenuGroup input = new MenuGroup("Appetizers");
        when(menuGroupRepo.existsByName("Appetizers")).thenReturn(false);
        when(menuGroupRepo.save(any(MenuGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuGroup saved = menuGroupService.createMenuGroup(input);

        assertThat(saved.getName()).isEqualTo("Appetizers");
        assertThat(saved.isActive()).isTrue();
        verify(menuGroupRepo).save(input);
    }

    @Test
    @DisplayName("createMenuGroup: explicitly inactive group is accepted and stays inactive")
    void createMenuGroupInactiveIsAccepted() {
        MenuGroup input = new MenuGroup("Retired Section");
        input.setActive(false);
        when(menuGroupRepo.existsByName("Retired Section")).thenReturn(false);
        when(menuGroupRepo.save(any(MenuGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuGroup saved = menuGroupService.createMenuGroup(input);

        assertThat(saved.isActive()).isFalse();
    }

    @Test
    @DisplayName("createMenuGroup: blank name is rejected and nothing is saved")
    void createMenuGroupBlankNameIsRejected() {
        assertThatThrownBy(() -> menuGroupService.createMenuGroup(new MenuGroup("   ")))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("Name must be provided");

        verify(menuGroupRepo, never()).save(any(MenuGroup.class));
    }

    @Test
    @DisplayName("createMenuGroup: null name is rejected and nothing is saved")
    void createMenuGroupNullNameIsRejected() {
        assertThatThrownBy(() -> menuGroupService.createMenuGroup(new MenuGroup(null)))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("Name must be provided");

        verify(menuGroupRepo, never()).save(any(MenuGroup.class));
    }

    @Test
    @DisplayName("createMenuGroup: duplicate name is rejected and nothing is saved")
    void createMenuGroupDuplicateNameIsRejected() {
        when(menuGroupRepo.existsByName("Appetizers")).thenReturn(true);

        assertThatThrownBy(() -> menuGroupService.createMenuGroup(new MenuGroup("Appetizers")))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("Menu group name already exists");

        verify(menuGroupRepo, never()).save(any(MenuGroup.class));
    }

    // --- getMenuGroupById ---

    @Test
    @DisplayName("getMenuGroupById: returns the group when it exists")
    void getMenuGroupByIdReturnsExisting() {
        MenuGroup existing = new MenuGroup("Appetizers");
        when(menuGroupRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(menuGroupService.getMenuGroupById(1L)).contains(existing);
    }

    @Test
    @DisplayName("getMenuGroupById: returns empty when the group does not exist")
    void getMenuGroupByIdReturnsEmptyForMissing() {
        when(menuGroupRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(menuGroupService.getMenuGroupById(99L)).isEmpty();
    }

    @Test
    @DisplayName("getMenuGroupById: null id is rejected")
    void getMenuGroupByIdRejectsNullId() {
        assertThatThrownBy(() -> menuGroupService.getMenuGroupById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- updateMenuGroup ---

    @Test
    @DisplayName("updateMenuGroup: applies new name and active flag")
    void updateMenuGroupAppliesChanges() {
        MenuGroup existing = new MenuGroup("Appetizers");
        MenuGroup changes = new MenuGroup("Starters");
        changes.setActive(false);
        when(menuGroupRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuGroupRepo.existsByNameAndIdNot("Starters", 1L)).thenReturn(false);
        when(menuGroupRepo.save(any(MenuGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuGroup updated = menuGroupService.updateMenuGroup(1L, changes);

        assertThat(updated.getName()).isEqualTo("Starters");
        assertThat(updated.isActive()).isFalse();
        verify(menuGroupRepo).save(existing);
    }

    @Test
    @DisplayName("updateMenuGroup: keeping the group's own name is allowed")
    void updateMenuGroupAllowsKeepingOwnName() {
        MenuGroup existing = new MenuGroup("Appetizers");
        MenuGroup changes = new MenuGroup("Appetizers");
        when(menuGroupRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuGroupRepo.existsByNameAndIdNot("Appetizers", 1L)).thenReturn(false);
        when(menuGroupRepo.save(any(MenuGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuGroup updated = menuGroupService.updateMenuGroup(1L, changes);

        assertThat(updated.getName()).isEqualTo("Appetizers");
    }

    @Test
    @DisplayName("updateMenuGroup: name used by another group is rejected")
    void updateMenuGroupRejectsNameOwnedByAnother() {
        MenuGroup existing = new MenuGroup("Appetizers");
        MenuGroup changes = new MenuGroup("Desserts");
        when(menuGroupRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuGroupRepo.existsByNameAndIdNot("Desserts", 1L)).thenReturn(true);

        assertThatThrownBy(() -> menuGroupService.updateMenuGroup(1L, changes))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("Menu group name already exists");

        verify(menuGroupRepo, never()).save(any(MenuGroup.class));
    }

    @Test
    @DisplayName("updateMenuGroup: blank name is rejected before any lookup")
    void updateMenuGroupRejectsBlankName() {
        assertThatThrownBy(() -> menuGroupService.updateMenuGroup(1L, new MenuGroup("  ")))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("Name must be provided");

        verify(menuGroupRepo, never()).save(any(MenuGroup.class));
    }

    @Test
    @DisplayName("updateMenuGroup: throws MenuGroupNotFoundException when the group does not exist")
    void updateMenuGroupThrowsNotFoundForMissing() {
        when(menuGroupRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuGroupService.updateMenuGroup(99L, new MenuGroup("Ghost")))
                .isInstanceOf(MenuGroupNotFoundException.class)
                .hasMessageContaining("not found");
    }

    // --- deleteMenuGroup ---

    @Test
    @DisplayName("deleteMenuGroup: unassigned group is deleted")
    void deleteMenuGroupWhenUnassigned() {
        when(menuGroupRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.countByMenuGroupId(1L)).thenReturn(0L);

        menuGroupService.deleteMenuGroup(1L);

        verify(menuGroupRepo).deleteById(1L);
    }

    @Test
    @DisplayName("deleteMenuGroup: group still assigned to a menu is rejected and nothing is deleted")
    void deleteMenuGroupBlockedByAssignment() {
        when(menuGroupRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.countByMenuGroupId(1L)).thenReturn(2L);

        assertThatThrownBy(() -> menuGroupService.deleteMenuGroup(1L))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("still assigned");

        verify(menuGroupRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteMenuGroup: throws MenuGroupNotFoundException for missing group")
    void deleteMenuGroupThrowsForMissing() {
        when(menuGroupRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> menuGroupService.deleteMenuGroup(99L))
                .isInstanceOf(MenuGroupNotFoundException.class);

        verify(menuGroupRepo, never()).deleteById(any());
    }

    // --- getAllMenuGroups ---

    @Test
    @DisplayName("getAllMenuGroups: returns every group in the repository")
    void getAllMenuGroupsReturnsAll() {
        when(menuGroupRepo.findAll()).thenReturn(List.of(
                new MenuGroup("Appetizers"),
                new MenuGroup("Desserts")));

        List<MenuGroup> groups = menuGroupService.getAllMenuGroups();

        assertThat(groups).hasSize(2)
                .extracting(MenuGroup::getName)
                .containsExactly("Appetizers", "Desserts");
    }
}
