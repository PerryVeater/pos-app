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
import com.example.posapp.exception.ModifierNotFoundException;
import com.example.posapp.exception.ModifierValidationException;
import com.example.posapp.repository.ModifierGroupAssignmentRepository;
import com.example.posapp.repository.ModifierRepository;

/**
 * Unit tests for the {@link ModifierService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service layer in
 * isolation: name validation, price-adjustment presence, and the guard that
 * blocks deleting a modifier still assigned to a group.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class ModifierServiceTest {

    @Mock
    private ModifierRepository modifierRepo;

    @Mock
    private ModifierGroupAssignmentRepository assignmentRepo;

    @InjectMocks
    private ModifierService modifierService;

    private static Modifier modifier(String name, String priceAdjustment) {
        return new Modifier(name, priceAdjustment == null
                ? null
                : new BigDecimal(priceAdjustment));
    }

    // --- createModifier ---

    @Test
    @DisplayName("createModifier: valid modifier is saved and returned")
    void createModifierValidIsSaved() {
        Modifier input = modifier("Extra cheese", "1.50");
        when(modifierRepo.save(any(Modifier.class))).thenAnswer(inv -> inv.getArgument(0));

        Modifier saved = modifierService.createModifier(input);

        assertThat(saved.getName()).isEqualTo("Extra cheese");
        assertThat(saved.getPriceAdjustment()).isEqualByComparingTo("1.50");
        assertThat(saved.isActive()).isTrue();
        verify(modifierRepo).save(input);
    }

    @Test
    @DisplayName("createModifier: negative adjustment (discount) is allowed")
    void createModifierAllowsNegativeAdjustment() {
        Modifier input = modifier("Loyalty discount", "-0.50");
        when(modifierRepo.save(any(Modifier.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(modifierService.createModifier(input).getPriceAdjustment())
                .isEqualByComparingTo("-0.50");
    }

    @Test
    @DisplayName("createModifier: zero adjustment is allowed")
    void createModifierAllowsZeroAdjustment() {
        Modifier input = modifier("No change", "0.00");
        when(modifierRepo.save(any(Modifier.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(modifierService.createModifier(input).getPriceAdjustment()).isZero();
    }

    @Test
    @DisplayName("createModifier: blank name is rejected and nothing is saved")
    void createModifierBlankNameIsRejected() {
        assertThatThrownBy(() -> modifierService.createModifier(modifier("   ", "1.00")))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("Name must be provided");

        verify(modifierRepo, never()).save(any(Modifier.class));
    }

    @Test
    @DisplayName("createModifier: null name is rejected and nothing is saved")
    void createModifierNullNameIsRejected() {
        assertThatThrownBy(() -> modifierService.createModifier(modifier(null, "1.00")))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("Name must be provided");

        verify(modifierRepo, never()).save(any(Modifier.class));
    }

    @Test
    @DisplayName("createModifier: null price adjustment is rejected")
    void createModifierNullAdjustmentIsRejected() {
        assertThatThrownBy(() -> modifierService.createModifier(modifier("No price", null)))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("Price adjustment");

        verify(modifierRepo, never()).save(any(Modifier.class));
    }

    // --- getModifierById ---

    @Test
    @DisplayName("getModifierById: returns the modifier when it exists")
    void getModifierByIdReturnsExisting() {
        Modifier existing = modifier("Extra cheese", "1.50");
        when(modifierRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(modifierService.getModifierById(1L)).contains(existing);
    }

    @Test
    @DisplayName("getModifierById: returns empty when the modifier does not exist")
    void getModifierByIdReturnsEmptyForMissing() {
        when(modifierRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(modifierService.getModifierById(99L)).isEmpty();
    }

    @Test
    @DisplayName("getModifierById: null id is rejected")
    void getModifierByIdRejectsNullId() {
        assertThatThrownBy(() -> modifierService.getModifierById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- updateModifier ---

    @Test
    @DisplayName("updateModifier: applies new name, price adjustment, and active flag")
    void updateModifierAppliesChanges() {
        Modifier existing = modifier("Extra cheese", "1.50");
        Modifier changes = new Modifier("Double cheese", new BigDecimal("3.00"));
        changes.setActive(false);
        when(modifierRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(modifierRepo.save(any(Modifier.class))).thenAnswer(inv -> inv.getArgument(0));

        Modifier updated = modifierService.updateModifier(1L, changes);

        assertThat(updated.getName()).isEqualTo("Double cheese");
        assertThat(updated.getPriceAdjustment()).isEqualByComparingTo("3.00");
        assertThat(updated.isActive()).isFalse();
        verify(modifierRepo).save(existing);
    }

    @Test
    @DisplayName("updateModifier: throws ModifierNotFoundException for a missing modifier")
    void updateModifierThrowsNotFoundForMissing() {
        when(modifierRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> modifierService.updateModifier(99L, modifier("Ghost", "1.00")))
                .isInstanceOf(ModifierNotFoundException.class);
    }

    @Test
    @DisplayName("updateModifier: blank name is rejected before any lookup")
    void updateModifierRejectsBlankName() {
        assertThatThrownBy(() -> modifierService.updateModifier(1L, modifier("   ", "1.00")))
                .isInstanceOf(ModifierValidationException.class);

        verify(modifierRepo, never()).save(any(Modifier.class));
    }

    @Test
    @DisplayName("updateModifier: null price adjustment is rejected")
    void updateModifierRejectsNullAdjustment() {
        assertThatThrownBy(() -> modifierService.updateModifier(1L, modifier("X", null)))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("Price adjustment");

        verify(modifierRepo, never()).save(any(Modifier.class));
    }

    // --- deleteModifier ---

    @Test
    @DisplayName("deleteModifier: unassigned modifier is deleted")
    void deleteModifierWhenUnassigned() {
        when(modifierRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.countByModifierId(1L)).thenReturn(0L);

        modifierService.deleteModifier(1L);

        verify(modifierRepo).deleteById(1L);
    }

    @Test
    @DisplayName("deleteModifier: modifier still assigned to a group is rejected and nothing is deleted")
    void deleteModifierBlockedByAssignment() {
        when(modifierRepo.existsById(1L)).thenReturn(true);
        when(assignmentRepo.countByModifierId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> modifierService.deleteModifier(1L))
                .isInstanceOf(ModifierValidationException.class)
                .hasMessageContaining("still assigned");

        verify(modifierRepo, never()).deleteById(any());
    }

    @Test
    @DisplayName("deleteModifier: throws ModifierNotFoundException for a missing modifier")
    void deleteModifierThrowsForMissing() {
        when(modifierRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> modifierService.deleteModifier(99L))
                .isInstanceOf(ModifierNotFoundException.class);

        verify(modifierRepo, never()).deleteById(any());
    }

    // --- getAllModifiers ---

    @Test
    @DisplayName("getAllModifiers: returns every modifier in the repository")
    void getAllModifiersReturnsAll() {
        when(modifierRepo.findAll()).thenReturn(List.of(
                modifier("Extra cheese", "1.50"),
                modifier("No onions", "0.00")));

        List<Modifier> modifiers = modifierService.getAllModifiers();

        assertThat(modifiers).hasSize(2)
                .extracting(Modifier::getName)
                .containsExactly("Extra cheese", "No onions");
    }
}
