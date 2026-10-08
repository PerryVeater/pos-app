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

import com.example.posapp.entity.Menu;
import com.example.posapp.entity.MenuGroup;
import com.example.posapp.entity.MenuGroupAssignment;
import com.example.posapp.exception.MenuGroupNotFoundException;
import com.example.posapp.exception.MenuNotFoundException;
import com.example.posapp.exception.MenuValidationException;
import com.example.posapp.repository.MenuGroupAssignmentRepository;
import com.example.posapp.repository.MenuGroupRepository;
import com.example.posapp.repository.MenuRepository;

/**
 * Unit tests for the {@link MenuService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service layer in
 * isolation: name validation, duplicate-name rejection, assignment rules
 * (menu/group must exist and the pair must be unique), and delegation to
 * the repositories. No Spring context or database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock
    private MenuRepository menuRepo;

    @Mock
    private MenuGroupRepository menuGroupRepo;

    @Mock
    private MenuGroupAssignmentRepository assignmentRepo;

    @InjectMocks
    private MenuService menuService;

    // --- createMenu ---

    @Test
    @DisplayName("createMenu: valid menu is saved and returned")
    void createMenuValidIsSaved() {
        Menu input = new Menu("Lunch");
        when(menuRepo.existsByName("Lunch")).thenReturn(false);
        when(menuRepo.save(any(Menu.class))).thenAnswer(inv -> inv.getArgument(0));

        Menu saved = menuService.createMenu(input);

        assertThat(saved.getName()).isEqualTo("Lunch");
        assertThat(saved.isActive()).isTrue();
        verify(menuRepo).save(input);
    }

    @Test
    @DisplayName("createMenu: explicitly inactive menu is accepted and stays inactive")
    void createMenuInactiveIsAccepted() {
        Menu input = new Menu("Legacy");
        input.setActive(false);
        when(menuRepo.existsByName("Legacy")).thenReturn(false);
        when(menuRepo.save(any(Menu.class))).thenAnswer(inv -> inv.getArgument(0));

        Menu saved = menuService.createMenu(input);

        assertThat(saved.isActive()).isFalse();
        verify(menuRepo).save(input);
    }

    @Test
    @DisplayName("createMenu: blank name is rejected and nothing is saved")
    void createMenuBlankNameIsRejected() {
        Menu input = new Menu("   ");

        assertThatThrownBy(() -> menuService.createMenu(input))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("Name must be provided");

        verify(menuRepo, never()).save(any(Menu.class));
    }

    @Test
    @DisplayName("createMenu: null name is rejected and nothing is saved")
    void createMenuNullNameIsRejected() {
        Menu input = new Menu(null);

        assertThatThrownBy(() -> menuService.createMenu(input))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("Name must be provided");

        verify(menuRepo, never()).save(any(Menu.class));
    }

    @Test
    @DisplayName("createMenu: duplicate name is rejected and nothing is saved")
    void createMenuDuplicateNameIsRejected() {
        Menu input = new Menu("Lunch");
        when(menuRepo.existsByName("Lunch")).thenReturn(true);

        assertThatThrownBy(() -> menuService.createMenu(input))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("Menu name already exists");

        verify(menuRepo, never()).save(any(Menu.class));
    }

    // --- getMenuById ---

    @Test
    @DisplayName("getMenuById: returns the menu when it exists")
    void getMenuByIdReturnsExisting() {
        Menu existing = new Menu("Lunch");
        when(menuRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(menuService.getMenuById(1L)).contains(existing);
    }

    @Test
    @DisplayName("getMenuById: returns empty when the menu does not exist")
    void getMenuByIdReturnsEmptyForMissing() {
        when(menuRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(menuService.getMenuById(99L)).isEmpty();
    }

    @Test
    @DisplayName("getMenuById: null id is rejected")
    void getMenuByIdRejectsNullId() {
        assertThatThrownBy(() -> menuService.getMenuById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- updateMenu ---

    @Test
    @DisplayName("updateMenu: applies new name and active flag to the existing menu")
    void updateMenuAppliesChanges() {
        Menu existing = new Menu("Lunch");
        Menu changes = new Menu("Lunch Special");
        changes.setActive(false);
        when(menuRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuRepo.existsByNameAndIdNot("Lunch Special", 1L)).thenReturn(false);
        when(menuRepo.save(any(Menu.class))).thenAnswer(inv -> inv.getArgument(0));

        Menu updated = menuService.updateMenu(1L, changes);

        assertThat(updated.getName()).isEqualTo("Lunch Special");
        assertThat(updated.isActive()).isFalse();
        verify(menuRepo).save(existing);
    }

    @Test
    @DisplayName("updateMenu: keeping the menu's own name is allowed")
    void updateMenuAllowsKeepingOwnName() {
        Menu existing = new Menu("Lunch");
        Menu changes = new Menu("Lunch");
        when(menuRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuRepo.existsByNameAndIdNot("Lunch", 1L)).thenReturn(false);
        when(menuRepo.save(any(Menu.class))).thenAnswer(inv -> inv.getArgument(0));

        Menu updated = menuService.updateMenu(1L, changes);

        assertThat(updated.getName()).isEqualTo("Lunch");
        verify(menuRepo).save(existing);
    }

    @Test
    @DisplayName("updateMenu: name already used by another menu is rejected and nothing is saved")
    void updateMenuRejectsNameOwnedByAnother() {
        Menu existing = new Menu("Lunch");
        Menu changes = new Menu("Dinner");
        when(menuRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(menuRepo.existsByNameAndIdNot("Dinner", 1L)).thenReturn(true);

        assertThatThrownBy(() -> menuService.updateMenu(1L, changes))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("Menu name already exists");

        verify(menuRepo, never()).save(any(Menu.class));
    }

    @Test
    @DisplayName("updateMenu: blank name is rejected before any repository lookup")
    void updateMenuRejectsBlankName() {
        assertThatThrownBy(() -> menuService.updateMenu(1L, new Menu("  ")))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("Name must be provided");

        verify(menuRepo, never()).save(any(Menu.class));
    }

    @Test
    @DisplayName("updateMenu: throws MenuNotFoundException when the menu does not exist")
    void updateMenuThrowsNotFoundForMissing() {
        when(menuRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuService.updateMenu(99L, new Menu("Ghost")))
                .isInstanceOf(MenuNotFoundException.class)
                .hasMessageContaining("not found");
    }

    // --- deleteMenu / getAllMenus ---

    @Test
    @DisplayName("deleteMenu: delegates to the repository")
    void deleteMenuDelegatesToRepository() {
        menuService.deleteMenu(1L);

        verify(menuRepo).deleteById(1L);
    }

    @Test
    @DisplayName("getAllMenus: returns every menu in the repository")
    void getAllMenusReturnsAll() {
        when(menuRepo.findAll()).thenReturn(List.of(
                new Menu("Lunch"),
                new Menu("Dinner")));

        List<Menu> menus = menuService.getAllMenus();

        assertThat(menus).hasSize(2)
                .extracting(Menu::getName)
                .containsExactly("Lunch", "Dinner");
    }

    // --- listAssignments ---

    @Test
    @DisplayName("listAssignments: returns the menu's assignments in display order")
    void listAssignmentsReturnsOrdered() {
        Menu lunch = new Menu("Lunch");
        MenuGroup apps = new MenuGroup("Appetizers");
        MenuGroup desserts = new MenuGroup("Desserts");
        MenuGroupAssignment first = new MenuGroupAssignment(lunch, apps, 1);
        MenuGroupAssignment second = new MenuGroupAssignment(lunch, desserts, 2);
        when(menuRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.findByMenuIdOrderByDisplayOrder(1L)).thenReturn(List.of(first, second));

        List<MenuGroupAssignment> result = menuService.listAssignments(1L);

        assertThat(result).containsExactly(first, second);
    }

    @Test
    @DisplayName("listAssignments: throws MenuNotFoundException for a missing menu")
    void listAssignmentsThrowsForMissingMenu() {
        when(menuRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> menuService.listAssignments(99L))
                .isInstanceOf(MenuNotFoundException.class)
                .hasMessageContaining("not found");
    }

    // --- assignGroup ---

    @Test
    @DisplayName("assignGroup: existing menu and unassigned group create a new assignment")
    void assignGroupCreatesAssignment() {
        Menu lunch = new Menu("Lunch");
        MenuGroup apps = new MenuGroup("Appetizers");
        when(menuRepo.findById(1L)).thenReturn(Optional.of(lunch));
        when(menuGroupRepo.findById(2L)).thenReturn(Optional.of(apps));
        when(assignmentRepo.existsByMenuIdAndMenuGroupId(1L, 2L)).thenReturn(false);
        when(assignmentRepo.save(any(MenuGroupAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        MenuGroupAssignment saved = menuService.assignGroup(1L, 2L, 3);

        assertThat(saved.getMenu()).isSameAs(lunch);
        assertThat(saved.getMenuGroup()).isSameAs(apps);
        assertThat(saved.getDisplayOrder()).isEqualTo(3);
        verify(assignmentRepo).save(any(MenuGroupAssignment.class));
    }

    @Test
    @DisplayName("assignGroup: missing menu throws MenuNotFoundException and nothing is saved")
    void assignGroupMissingMenuThrows() {
        when(menuRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuService.assignGroup(99L, 1L, 1))
                .isInstanceOf(MenuNotFoundException.class);

        verify(assignmentRepo, never()).save(any(MenuGroupAssignment.class));
    }

    @Test
    @DisplayName("assignGroup: missing menu group throws MenuGroupNotFoundException and nothing is saved")
    void assignGroupMissingMenuGroupThrows() {
        when(menuRepo.findById(1L)).thenReturn(Optional.of(new Menu("Lunch")));
        when(menuGroupRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuService.assignGroup(1L, 99L, 1))
                .isInstanceOf(MenuGroupNotFoundException.class);

        verify(assignmentRepo, never()).save(any(MenuGroupAssignment.class));
    }

    @Test
    @DisplayName("assignGroup: duplicate (menu, group) pair throws MenuValidationException")
    void assignGroupDuplicateThrows() {
        when(menuRepo.findById(1L)).thenReturn(Optional.of(new Menu("Lunch")));
        when(menuGroupRepo.findById(2L)).thenReturn(Optional.of(new MenuGroup("Appetizers")));
        when(assignmentRepo.existsByMenuIdAndMenuGroupId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> menuService.assignGroup(1L, 2L, 1))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("already assigned");

        verify(assignmentRepo, never()).save(any(MenuGroupAssignment.class));
    }

    // --- unassignGroup ---

    @Test
    @DisplayName("unassignGroup: existing assignment is deleted")
    void unassignGroupDeletesAssignment() {
        MenuGroupAssignment assignment = new MenuGroupAssignment(
                new Menu("Lunch"), new MenuGroup("Appetizers"), 1);
        when(menuRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.findByMenuIdAndMenuGroupId(1L, 2L)).thenReturn(Optional.of(assignment));

        menuService.unassignGroup(1L, 2L);

        verify(assignmentRepo).delete(assignment);
    }

    @Test
    @DisplayName("unassignGroup: missing menu throws MenuNotFoundException")
    void unassignGroupMissingMenuThrows() {
        when(menuRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> menuService.unassignGroup(99L, 1L))
                .isInstanceOf(MenuNotFoundException.class);

        verify(assignmentRepo, never()).delete(any(MenuGroupAssignment.class));
    }

    @Test
    @DisplayName("unassignGroup: missing assignment throws MenuGroupNotFoundException")
    void unassignGroupMissingAssignmentThrows() {
        when(menuRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.findByMenuIdAndMenuGroupId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> menuService.unassignGroup(1L, 2L))
                .isInstanceOf(MenuGroupNotFoundException.class);

        verify(assignmentRepo, never()).delete(any(MenuGroupAssignment.class));
    }
}
