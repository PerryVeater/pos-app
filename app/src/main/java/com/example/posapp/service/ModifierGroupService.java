package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

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
 * Service layer for {@link ModifierGroup} entities and their modifier
 * assignments.
 * <p>
 * Enforces the name-required / name-unique rules, the selection policy
 * ({@code minSelections >= 0}, {@code maxSelections >= minSelections}), and
 * manages the (group, modifier) assignment records that display reusable
 * {@link Modifier}s inside a group in caller-supplied order.
 * </p>
 */
@Service
public class ModifierGroupService {

    private final ModifierGroupRepository modifierGroupRepo;
    private final ModifierGroupAssignmentRepository assignmentRepo;
    private final ModifierRepository modifierRepo;
    private final MenuItemModifierGroupAssignmentRepository mimgAssignmentRepo;

    /**
     * Constructor for ModifierGroupService.
     * @param modifierGroupRepo the repository for modifier groups
     * @param assignmentRepo the repository for modifier group ↔ modifier assignments
     * @param modifierRepo the repository for modifiers
     * @param mimgAssignmentRepo the repository for MenuItem ↔ ModifierGroup
     *        assignments, used by {@link #deleteModifierGroup(Long)} to block
     *        deletion while the group is still attached to a menu item
     */
    public ModifierGroupService(ModifierGroupRepository modifierGroupRepo,
                                ModifierGroupAssignmentRepository assignmentRepo,
                                ModifierRepository modifierRepo,
                                MenuItemModifierGroupAssignmentRepository mimgAssignmentRepo) {
        this.modifierGroupRepo = modifierGroupRepo;
        this.assignmentRepo = assignmentRepo;
        this.modifierRepo = modifierRepo;
        this.mimgAssignmentRepo = mimgAssignmentRepo;
    }

    /**
     * Create a new modifier group after validating its name and selection policy.
     * @param group the transient group to save
     * @return the saved group
     * @throws ModifierValidationException if the name is blank, duplicated,
     *         or the selection policy is invalid
     */
    public ModifierGroup createModifierGroup(ModifierGroup group) {
        validateName(group.getName());
        validateSelectionPolicy(group.getMinSelections(), group.getMaxSelections());
        if (modifierGroupRepo.existsByName(group.getName())) {
            throw new ModifierValidationException(
                    "Modifier group name already exists: " + group.getName());
        }
        return modifierGroupRepo.save(group);
    }

    /**
     * Update an existing modifier group. The name is validated for uniqueness
     * against other groups; keeping the group's own name is allowed.
     * @param id the modifier group ID
     * @param updated the replacement values
     * @return the updated group
     * @throws ModifierGroupNotFoundException if no group exists with the ID
     * @throws ModifierValidationException if the name is blank, used by another group,
     *         or the selection policy is invalid
     */
    public ModifierGroup updateModifierGroup(Long id, ModifierGroup updated) {
        validateName(updated.getName());
        validateSelectionPolicy(updated.getMinSelections(), updated.getMaxSelections());
        return modifierGroupRepo.findById(id)
                .map(existing -> {
                    if (modifierGroupRepo.existsByNameAndIdNot(updated.getName(), id)) {
                        throw new ModifierValidationException(
                                "Modifier group name already exists: " + updated.getName());
                    }
                    existing.setName(updated.getName());
                    existing.setMinSelections(updated.getMinSelections());
                    existing.setMaxSelections(updated.getMaxSelections());
                    existing.setActive(updated.isActive());
                    return modifierGroupRepo.save(existing);
                })
                .orElseThrow(() -> new ModifierGroupNotFoundException(id));
    }

    /**
     * Delete a modifier group by ID. Refuses to delete a group that is still
     * assigned to at least one menu item so callers must unassign it first.
     * Once the guard passes, attached assignments are removed through the JPA
     * cascade on {@link ModifierGroup#getAssignments()}; the referenced
     * modifiers stay intact.
     * @param id the modifier group ID
     * @throws ModifierValidationException if the group is still assigned to
     *         a menu item
     */
    public void deleteModifierGroup(Long id) {
        if (mimgAssignmentRepo.countByModifierGroupId(id) > 0) {
            throw new ModifierValidationException(
                    "Cannot delete modifier group still assigned to a menu item: " + id);
        }
        modifierGroupRepo.deleteById(id);
    }

    /**
     * Retrieve every modifier group.
     * @return the list of modifier groups
     */
    public List<ModifierGroup> getAllModifierGroups() {
        return modifierGroupRepo.findAll();
    }

    /**
     * Retrieve a modifier group by ID.
     * @param id the modifier group ID
     * @return the Optional containing the group if found
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<ModifierGroup> getModifierGroupById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return modifierGroupRepo.findById(id);
    }

    /**
     * Return the modifier assignments attached to a group, ordered by display position.
     * @param modifierGroupId the modifier group ID
     * @return the ordered assignments (empty when the group has none)
     * @throws ModifierGroupNotFoundException if no group exists with the ID
     */
    public List<ModifierGroupAssignment> listModifierAssignments(Long modifierGroupId) {
        if (!modifierGroupRepo.existsById(modifierGroupId)) {
            throw new ModifierGroupNotFoundException(modifierGroupId);
        }
        return assignmentRepo.findByModifierGroupIdOrderByDisplayOrder(modifierGroupId);
    }

    /**
     * Assign a modifier to a modifier group at a specific display position.
     * @param modifierGroupId the modifier group ID
     * @param modifierId the modifier ID
     * @param displayOrder the position of the modifier within the group
     * @return the saved assignment
     * @throws ModifierGroupNotFoundException if the group does not exist
     * @throws ModifierNotFoundException if the modifier does not exist
     * @throws ModifierValidationException if the modifier is already assigned to the group
     */
    public ModifierGroupAssignment assignModifier(Long modifierGroupId, Long modifierId, int displayOrder) {
        ModifierGroup group = modifierGroupRepo.findById(modifierGroupId)
                .orElseThrow(() -> new ModifierGroupNotFoundException(modifierGroupId));
        Modifier modifier = modifierRepo.findById(modifierId)
                .orElseThrow(() -> new ModifierNotFoundException(modifierId));
        if (assignmentRepo.existsByModifierGroupIdAndModifierId(modifierGroupId, modifierId)) {
            throw new ModifierValidationException(
                    "Modifier already assigned to modifier group: modifierGroupId="
                            + modifierGroupId + ", modifierId=" + modifierId);
        }
        return assignmentRepo.save(new ModifierGroupAssignment(group, modifier, displayOrder));
    }

    /**
     * Remove a specific assignment between a modifier group and a modifier.
     * A missing pair is reported as 404 via {@link ModifierNotFoundException}
     * because there is no assignment to remove. The underlying modifier is
     * never deleted.
     * @param modifierGroupId the modifier group ID
     * @param modifierId the modifier ID
     * @throws ModifierGroupNotFoundException if the group does not exist
     * @throws ModifierNotFoundException if the assignment does not exist
     */
    public void unassignModifier(Long modifierGroupId, Long modifierId) {
        if (!modifierGroupRepo.existsById(modifierGroupId)) {
            throw new ModifierGroupNotFoundException(modifierGroupId);
        }
        ModifierGroupAssignment assignment = assignmentRepo
                .findByModifierGroupIdAndModifierId(modifierGroupId, modifierId)
                .orElseThrow(() -> new ModifierNotFoundException(modifierId));
        assignmentRepo.delete(assignment);
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
     * Reject nonsensical selection policies. Mirrors the two DB check
     * constraints {@code chk_modifier_group_min_non_negative} and
     * {@code chk_modifier_group_max_gte_min} as a friendly first line of
     * defense.
     * @param min the minimum number of selections
     * @param max the maximum number of selections
     * @throws ModifierValidationException if either rule is violated
     */
    private static void validateSelectionPolicy(int min, int max) {
        if (min < 0) {
            throw new ModifierValidationException("minSelections cannot be negative");
        }
        if (max < min) {
            throw new ModifierValidationException(
                    "maxSelections cannot be less than minSelections: min=" + min + ", max=" + max);
        }
    }
}
