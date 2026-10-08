package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.Modifier;
import com.example.posapp.exception.ModifierNotFoundException;
import com.example.posapp.exception.ModifierValidationException;
import com.example.posapp.repository.ModifierGroupAssignmentRepository;
import com.example.posapp.repository.ModifierRepository;

/**
 * Service layer for {@link Modifier} entities.
 * <p>
 * Modifiers are reusable across modifier groups; this service enforces the
 * name-required rule and refuses to delete a modifier that is still
 * assigned to at least one group, so callers must first unassign it.
 * </p>
 */
@Service
public class ModifierService {

    private final ModifierRepository modifierRepo;
    private final ModifierGroupAssignmentRepository assignmentRepo;

    /**
     * Constructor for ModifierService.
     * @param modifierRepo the repository for modifiers
     * @param assignmentRepo the repository for modifier group ↔ modifier assignments
     */
    public ModifierService(ModifierRepository modifierRepo,
                           ModifierGroupAssignmentRepository assignmentRepo) {
        this.modifierRepo = modifierRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /**
     * Create a new modifier after validating its name and price adjustment.
     * @param modifier the transient modifier to save
     * @return the saved modifier
     * @throws ModifierValidationException if the name is blank or the price adjustment is missing
     */
    public Modifier createModifier(Modifier modifier) {
        validateName(modifier.getName());
        validatePriceAdjustment(modifier.getPriceAdjustment());
        return modifierRepo.save(modifier);
    }

    /**
     * Update an existing modifier.
     * @param id the modifier ID
     * @param updated the replacement values
     * @return the updated modifier
     * @throws ModifierNotFoundException if no modifier exists with the ID
     * @throws ModifierValidationException if the name is blank or the price adjustment is missing
     */
    public Modifier updateModifier(Long id, Modifier updated) {
        validateName(updated.getName());
        validatePriceAdjustment(updated.getPriceAdjustment());
        return modifierRepo.findById(id)
                .map(existing -> {
                    existing.setName(updated.getName());
                    existing.setPriceAdjustment(updated.getPriceAdjustment());
                    existing.setActive(updated.isActive());
                    return modifierRepo.save(existing);
                })
                .orElseThrow(() -> new ModifierNotFoundException(id));
    }

    /**
     * Delete a modifier by ID. Refuses to delete a modifier that is still
     * assigned to at least one group so callers must unassign it first.
     * @param id the modifier ID
     * @throws ModifierNotFoundException if no modifier exists with the ID
     * @throws ModifierValidationException if the modifier is still assigned to a group
     */
    public void deleteModifier(Long id) {
        if (!modifierRepo.existsById(id)) {
            throw new ModifierNotFoundException(id);
        }
        if (assignmentRepo.countByModifierId(id) > 0) {
            throw new ModifierValidationException(
                    "Cannot delete modifier still assigned to a modifier group: " + id);
        }
        modifierRepo.deleteById(id);
    }

    /**
     * Retrieve every modifier.
     * @return the list of modifiers
     */
    public List<Modifier> getAllModifiers() {
        return modifierRepo.findAll();
    }

    /**
     * Retrieve a modifier by ID.
     * @param id the modifier ID
     * @return the Optional containing the modifier if found
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<Modifier> getModifierById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return modifierRepo.findById(id);
    }

    /**
     * Reject blank or missing names for any write operation.
     * @param name the name to validate
     * @throws ModifierValidationException if the name is missing or blank
     */
    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new ModifierValidationException("Name must be provided");
        }
    }

    /**
     * Reject missing price adjustments; negative adjustments are allowed so
     * discounts remain expressible.
     * @param priceAdjustment the price adjustment to validate
     * @throws ModifierValidationException if the adjustment is null
     */
    private static void validatePriceAdjustment(java.math.BigDecimal priceAdjustment) {
        if (priceAdjustment == null) {
            throw new ModifierValidationException("Price adjustment must be provided");
        }
    }
}
