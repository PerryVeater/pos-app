package com.example.posapp.service;

import java.math.BigDecimal;
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

import com.example.posapp.entity.MenuItem;
import com.example.posapp.entity.MenuItemModifierGroupAssignment;
import com.example.posapp.entity.ModifierGroup;
import com.example.posapp.exception.MenuValidationException;
import com.example.posapp.exception.MenuItemNotFoundException;
import com.example.posapp.exception.ModifierGroupNotFoundException;
import com.example.posapp.repository.MenuItemModifierGroupAssignmentRepository;
import com.example.posapp.repository.MenuItemRepository;
import com.example.posapp.repository.ModifierGroupRepository;

/**
 * Unit tests for the {@link MenuItemModifierGroupService} assignment
 * lifecycle.
 * <p>
 * These tests exercise the cross-domain wiring between MenuItem and
 * ModifierGroup in isolation: list, assign, unassign. They verify that
 * the service enforces existence of both aggregates, rejects duplicate
 * (item, group) pairs, and never deletes the underlying ModifierGroup
 * when the assignment is removed.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MenuItemModifierGroupServiceTest {

    @Mock
    private MenuItemRepository menuItemRepo;

    @Mock
    private ModifierGroupRepository modifierGroupRepo;

    @Mock
    private MenuItemModifierGroupAssignmentRepository assignmentRepo;

    @InjectMocks
    private MenuItemModifierGroupService service;

    private static MenuItem item(String name) {
        return new MenuItem(name, "SKU-" + name, new BigDecimal("1.00"), true);
    }

    private static ModifierGroup group(String name, int min, int max) {
        return new ModifierGroup(name, min, max);
    }

    // --- listAssignments ---

    @Test
    @DisplayName("listAssignments: returns assignments in display order")
    void listAssignmentsReturnsOrdered() {
        MenuItem item = item("Pizza");
        ModifierGroup toppings = group("Toppings", 0, 3);
        ModifierGroup crusts = group("Crusts", 1, 1);
        when(menuItemRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.findByMenuItemIdOrderByDisplayOrder(1L)).thenReturn(List.of(
                new MenuItemModifierGroupAssignment(item, toppings, 1),
                new MenuItemModifierGroupAssignment(item, crusts, 2)));

        List<MenuItemModifierGroupAssignment> result = service.listAssignments(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getModifierGroup().getName()).isEqualTo("Toppings");
        assertThat(result.get(1).getModifierGroup().getName()).isEqualTo("Crusts");
    }

    @Test
    @DisplayName("listAssignments: empty list when the item has no assignments")
    void listAssignmentsReturnsEmptyWhenNone() {
        when(menuItemRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.findByMenuItemIdOrderByDisplayOrder(1L)).thenReturn(List.of());

        assertThat(service.listAssignments(1L)).isEmpty();
    }

    @Test
    @DisplayName("listAssignments: throws MenuItemNotFoundException for a missing item")
    void listAssignmentsThrowsForMissingItem() {
        when(menuItemRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.listAssignments(99L))
                .isInstanceOf(MenuItemNotFoundException.class);
    }

    // --- assign ---

    @Test
    @DisplayName("assign: existing item and unassigned group create a new assignment")
    void assignCreatesAssignment() {
        MenuItem item = item("Pizza");
        ModifierGroup group = group("Toppings", 0, 3);
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(item));
        when(modifierGroupRepo.findById(2L)).thenReturn(Optional.of(group));
        when(assignmentRepo.existsByMenuItemIdAndModifierGroupId(1L, 2L)).thenReturn(false);
        when(assignmentRepo.save(any(MenuItemModifierGroupAssignment.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        MenuItemModifierGroupAssignment saved = service.assign(1L, 2L, 5);

        assertThat(saved.getMenuItem()).isSameAs(item);
        assertThat(saved.getModifierGroup()).isSameAs(group);
        assertThat(saved.getDisplayOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("assign: missing item throws MenuItemNotFoundException")
    void assignMissingItemThrows() {
        when(menuItemRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(99L, 1L, 1))
                .isInstanceOf(MenuItemNotFoundException.class);

        verify(assignmentRepo, never()).save(any(MenuItemModifierGroupAssignment.class));
    }

    @Test
    @DisplayName("assign: missing modifier group throws ModifierGroupNotFoundException")
    void assignMissingGroupThrows() {
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(item("Pizza")));
        when(modifierGroupRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(1L, 99L, 1))
                .isInstanceOf(ModifierGroupNotFoundException.class);

        verify(assignmentRepo, never()).save(any(MenuItemModifierGroupAssignment.class));
    }

    @Test
    @DisplayName("assign: duplicate (item, group) pair throws MenuValidationException")
    void assignDuplicateThrows() {
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(item("Pizza")));
        when(modifierGroupRepo.findById(2L)).thenReturn(Optional.of(group("Toppings", 0, 3)));
        when(assignmentRepo.existsByMenuItemIdAndModifierGroupId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.assign(1L, 2L, 1))
                .isInstanceOf(MenuValidationException.class)
                .hasMessageContaining("already assigned");

        verify(assignmentRepo, never()).save(any(MenuItemModifierGroupAssignment.class));
    }

    // --- unassign ---

    @Test
    @DisplayName("unassign: existing assignment is deleted; underlying records stay intact")
    void unassignDeletesAssignment() {
        MenuItemModifierGroupAssignment assignment = new MenuItemModifierGroupAssignment(
                item("Pizza"), group("Toppings", 0, 3), 1);
        when(menuItemRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.findByMenuItemIdAndModifierGroupId(1L, 2L))
                .thenReturn(Optional.of(assignment));

        service.unassign(1L, 2L);

        verify(assignmentRepo).delete(assignment);
        // Service never deletes the underlying MenuItem or ModifierGroup.
        verify(menuItemRepo, never()).deleteById(any());
        verify(modifierGroupRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("unassign: missing item throws MenuItemNotFoundException")
    void unassignMissingItemThrows() {
        when(menuItemRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.unassign(99L, 1L))
                .isInstanceOf(MenuItemNotFoundException.class);

        verify(assignmentRepo, never()).delete(any(MenuItemModifierGroupAssignment.class));
    }

    @Test
    @DisplayName("unassign: missing assignment throws ModifierGroupNotFoundException")
    void unassignMissingAssignmentThrows() {
        when(menuItemRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.findByMenuItemIdAndModifierGroupId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unassign(1L, 2L))
                .isInstanceOf(ModifierGroupNotFoundException.class);

        verify(assignmentRepo, never()).delete(any(MenuItemModifierGroupAssignment.class));
    }
}
