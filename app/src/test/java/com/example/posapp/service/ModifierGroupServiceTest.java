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

import com.example.posapp.entity.Modifier;
import com.example.posapp.entity.ModifierGroup;
import com.example.posapp.entity.ModifierGroupAssignment;
import com.example.posapp.exception.ModifierGroupNotFoundException;
import com.example.posapp.exception.ModifierNotFoundException;
import com.example.posapp.exception.ModifierValidationException;
import com.example.posapp.repository.ModifierGroupAssignmentRepository;
import com.example.posapp.repository.ModifierGroupRepository;
import com.example.posapp.repository.MenuItemModifierGroupAssignmentRepository;
import com.example.posapp.repository.ModifierRepository;

/**
 * Unit tests for the {@link ModifierGroupService} business rules.
 * <p>
 * Modifier groups own the selection policy (min/max) and the (group,
 * modifier) assignment records. These tests exercise name validation,
 * duplicate-name rejection, selection-policy enforcement, and the
 * assignment lifecycle (missing group / missing modifier / duplicate pair).
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ModifierGroupServiceTest {

    @Mock
    private ModifierGroupRepository modifierGroupRepo;

    @Mock
    private ModifierGroupAssignmentRepository assignmentRepo;

    @Mock
    private ModifierRepository modifierRepo;

    @Mock
    private MenuItemModifierGroupAssignmentRepository mimgAssignmentRepo;

    @InjectMocks
    private ModifierGroupService modifierGroupService;

    private static ModifierGroup group(String name, int min, int max) {
        return new ModifierGroup(name, min, max);
    }

    // --- createModifierGroup ---

    @Test
    @DisplayName("createModifierGroup: valid group is saved and returned")
    void createModifierGroupValidIsSaved() {
        ModifierGroup input = group("Pizza toppings", 0, 3);
        when(modifierGroupRepo.existsByName("Pizza toppings")).thenReturn(false);
        when(modifierGroupRepo.save(any(ModifierGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        ModifierGroup saved = modifierGroupService.createModifierGroup(input);

        assertThat(saved.getName()).isEqualTo("Pizza toppings");
        assertThat(saved.getMinSelections()).isZero();
        assertThat(saved.getMaxSelections()).isEqualTo(3);
        assertThat(saved.isActive()).isTrue();
        verify(modifierGroupRepo).save(input);
    }

    @Test
    @DisplayName("createModifierGroup: blank name is rejected and nothing is saved")
    void createModifierGroupBlankNameIsRejected() {
        assertThatThrownBy(() -> modifierGroupService.createModifierGroup(group("   ", 0, 1)))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("Name must be provided");

        verify(modifierGroupRepo, never()).save(any(ModifierGroup.class));
    }

    @Test
    @DisplayName("createModifierGroup: duplicate name is rejected and nothing is saved")
    void createModifierGroupDuplicateNameIsRejected() {
        when(modifierGroupRepo.existsByName("Pizza toppings")).thenReturn(true);

        assertThatThrownBy(() -> modifierGroupService.createModifierGroup(group("Pizza toppings", 0, 3)))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("already exists");

        verify(modifierGroupRepo, never()).save(any(ModifierGroup.class));
    }

    @Test
    @DisplayName("createModifierGroup: negative minSelections is rejected")
    void createModifierGroupRejectsNegativeMin() {
        assertThatThrownBy(() -> modifierGroupService.createModifierGroup(group("X", -1, 3)))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("minSelections cannot be negative");

        verify(modifierGroupRepo, never()).save(any(ModifierGroup.class));
    }

    @Test
    @DisplayName("createModifierGroup: maxSelections below minSelections is rejected")
    void createModifierGroupRejectsMaxBelowMin() {
        assertThatThrownBy(() -> modifierGroupService.createModifierGroup(group("X", 3, 2)))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("maxSelections cannot be less than minSelections");

        verify(modifierGroupRepo, never()).save(any(ModifierGroup.class));
    }

    @Test
    @DisplayName("createModifierGroup: min equal to max is allowed (exactly-N policy)")
    void createModifierGroupAllowsMinEqualToMax() {
        ModifierGroup input = group("Pick exactly two", 2, 2);
        when(modifierGroupRepo.existsByName("Pick exactly two")).thenReturn(false);
        when(modifierGroupRepo.save(any(ModifierGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(modifierGroupService.createModifierGroup(input).getMaxSelections()).isEqualTo(2);
    }

    // --- updateModifierGroup ---

    @Test
    @DisplayName("updateModifierGroup: applies new name, policy, and active flag")
    void updateModifierGroupAppliesChanges() {
        ModifierGroup existing = group("Pizza toppings", 0, 3);
        ModifierGroup changes = group("Hot dog toppings", 1, 2);
        changes.setActive(false);
        when(modifierGroupRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(modifierGroupRepo.existsByNameAndIdNot("Hot dog toppings", 1L)).thenReturn(false);
        when(modifierGroupRepo.save(any(ModifierGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        ModifierGroup updated = modifierGroupService.updateModifierGroup(1L, changes);

        assertThat(updated.getName()).isEqualTo("Hot dog toppings");
        assertThat(updated.getMinSelections()).isEqualTo(1);
        assertThat(updated.getMaxSelections()).isEqualTo(2);
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    @DisplayName("updateModifierGroup: throws ModifierGroupNotFoundException for a missing group")
    void updateModifierGroupThrowsNotFoundForMissing() {
        when(modifierGroupRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modifierGroupService.updateModifierGroup(99L, group("Ghost", 0, 1)))
                .isInstanceOf(ModifierGroupNotFoundException.class);
    }

    @Test
    @DisplayName("updateModifierGroup: invalid selection policy is rejected before any lookup")
    void updateModifierGroupRejectsInvalidPolicy() {
        assertThatThrownBy(() -> modifierGroupService.updateModifierGroup(1L, group("X", 5, 2)))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("maxSelections");

        verify(modifierGroupRepo, never()).save(any(ModifierGroup.class));
    }

    @Test
    @DisplayName("updateModifierGroup: name used by another group is rejected")
    void updateModifierGroupRejectsNameOwnedByAnother() {
        ModifierGroup existing = group("Pizza toppings", 0, 3);
        ModifierGroup changes = group("Hot dog toppings", 0, 3);
        when(modifierGroupRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(modifierGroupRepo.existsByNameAndIdNot("Hot dog toppings", 1L)).thenReturn(true);

        assertThatThrownBy(() -> modifierGroupService.updateModifierGroup(1L, changes))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("already exists");

        verify(modifierGroupRepo, never()).save(any(ModifierGroup.class));
    }

    // --- deleteModifierGroup / getAllModifierGroups ---

    @Test
    @DisplayName("deleteModifierGroup: delegates to the repository (JPA cascade removes assignments)")
    void deleteModifierGroupDelegatesToRepository() {
        when(mimgAssignmentRepo.countByModifierGroupId(1L)).thenReturn(0L);

        modifierGroupService.deleteModifierGroup(1L);

        verify(modifierGroupRepo).deleteById(1L);
    }

    @Test
    @DisplayName("deleteModifierGroup: rejects deletion when still assigned to a menu item")
    void deleteModifierGroupWithMenuItemAssignmentThrows() {
        when(mimgAssignmentRepo.countByModifierGroupId(7L)).thenReturn(2L);

        assertThatThrownBy(() -> modifierGroupService.deleteModifierGroup(7L))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("still assigned to a menu item");

        verify(modifierGroupRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("getAllModifierGroups: returns every group")
    void getAllModifierGroupsReturnsAll() {
        when(modifierGroupRepo.findAll()).thenReturn(List.of(
                group("Pizza toppings", 0, 3),
                group("Burgers", 0, 5)));

        List<ModifierGroup> groups = modifierGroupService.getAllModifierGroups();

        assertThat(groups).hasSize(2)
                .extracting(ModifierGroup::getName)
                .containsExactly("Pizza toppings", "Burgers");
    }

    @Test
    @DisplayName("getModifierGroupById: null id is rejected")
    void getModifierGroupByIdRejectsNullId() {
        assertThatThrownBy(() -> modifierGroupService.getModifierGroupById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- listModifierAssignments ---

    @Test
    @DisplayName("listModifierAssignments: returns the group's assignments in display order")
    void listModifierAssignmentsReturnsOrdered() {
        ModifierGroup group = group("Pizza toppings", 0, 3);
        Modifier cheese = new Modifier("Extra cheese", new BigDecimal("1.00"));
        Modifier pepperoni = new Modifier("Pepperoni", new BigDecimal("2.00"));
        when(modifierGroupRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.findByModifierGroupIdOrderByDisplayOrder(1L)).thenReturn(List.of(
                new ModifierGroupAssignment(group, cheese, 1),
                new ModifierGroupAssignment(group, pepperoni, 2)));

        List<ModifierGroupAssignment> result = modifierGroupService.listModifierAssignments(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getModifier().getName()).isEqualTo("Extra cheese");
        assertThat(result.get(1).getModifier().getName()).isEqualTo("Pepperoni");
    }

    @Test
    @DisplayName("listModifierAssignments: throws ModifierGroupNotFoundException for a missing group")
    void listModifierAssignmentsThrowsForMissingGroup() {
        when(modifierGroupRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> modifierGroupService.listModifierAssignments(99L))
                .isInstanceOf(ModifierGroupNotFoundException.class);
    }

    // --- assignModifier ---

    @Test
    @DisplayName("assignModifier: existing group and unassigned modifier create a new assignment")
    void assignModifierCreatesAssignment() {
        ModifierGroup group = group("Pizza toppings", 0, 3);
        Modifier cheese = new Modifier("Extra cheese", new BigDecimal("1.00"));
        when(modifierGroupRepo.findById(1L)).thenReturn(Optional.of(group));
        when(modifierRepo.findById(2L)).thenReturn(Optional.of(cheese));
        when(assignmentRepo.existsByModifierGroupIdAndModifierId(1L, 2L)).thenReturn(false);
        when(assignmentRepo.save(any(ModifierGroupAssignment.class))).thenAnswer(inv -> inv.getArgument(0));

        ModifierGroupAssignment saved = modifierGroupService.assignModifier(1L, 2L, 5);

        assertThat(saved.getModifierGroup()).isSameAs(group);
        assertThat(saved.getModifier()).isSameAs(cheese);
        assertThat(saved.getDisplayOrder()).isEqualTo(5);
    }

    @Test
    @DisplayName("assignModifier: missing group throws ModifierGroupNotFoundException")
    void assignModifierMissingGroupThrows() {
        when(modifierGroupRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modifierGroupService.assignModifier(99L, 1L, 1))
                .isInstanceOf(ModifierGroupNotFoundException.class);

        verify(assignmentRepo, never()).save(any(ModifierGroupAssignment.class));
    }

    @Test
    @DisplayName("assignModifier: missing modifier throws ModifierNotFoundException")
    void assignModifierMissingModifierThrows() {
        when(modifierGroupRepo.findById(1L)).thenReturn(Optional.of(group("Pizza toppings", 0, 3)));
        when(modifierRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modifierGroupService.assignModifier(1L, 99L, 1))
                .isInstanceOf(ModifierNotFoundException.class);

        verify(assignmentRepo, never()).save(any(ModifierGroupAssignment.class));
    }

    @Test
    @DisplayName("assignModifier: duplicate (group, modifier) pair throws ModifierValidationException")
    void assignModifierDuplicateThrows() {
        when(modifierGroupRepo.findById(1L)).thenReturn(Optional.of(group("Pizza toppings", 0, 3)));
        when(modifierRepo.findById(2L)).thenReturn(Optional.of(new Modifier("Extra cheese", BigDecimal.ONE)));
        when(assignmentRepo.existsByModifierGroupIdAndModifierId(1L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> modifierGroupService.assignModifier(1L, 2L, 1))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("already assigned");

        verify(assignmentRepo, never()).save(any(ModifierGroupAssignment.class));
    }

    // --- unassignModifier ---

    @Test
    @DisplayName("unassignModifier: existing assignment is deleted")
    void unassignModifierDeletesAssignment() {
        ModifierGroupAssignment assignment = new ModifierGroupAssignment(
                group("Pizza toppings", 0, 3),
                new Modifier("Extra cheese", BigDecimal.ONE),
                1);
        when(modifierGroupRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.findByModifierGroupIdAndModifierId(1L, 2L)).thenReturn(Optional.of(assignment));

        modifierGroupService.unassignModifier(1L, 2L);

        verify(assignmentRepo).delete(assignment);
    }

    @Test
    @DisplayName("unassignModifier: missing group throws ModifierGroupNotFoundException")
    void unassignModifierMissingGroupThrows() {
        when(modifierGroupRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> modifierGroupService.unassignModifier(99L, 1L))
                .isInstanceOf(ModifierGroupNotFoundException.class);

        verify(assignmentRepo, never()).delete(any(ModifierGroupAssignment.class));
    }

    @Test
    @DisplayName("unassignModifier: missing assignment throws ModifierNotFoundException")
    void unassignModifierMissingAssignmentThrows() {
        when(modifierGroupRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.findByModifierGroupIdAndModifierId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modifierGroupService.unassignModifier(1L, 2L))
                .isInstanceOf(ModifierNotFoundException.class);

        verify(assignmentRepo, never()).delete(any(ModifierGroupAssignment.class));
    }
}
