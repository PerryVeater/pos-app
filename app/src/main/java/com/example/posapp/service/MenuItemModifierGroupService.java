package com.example.posapp.service;

import java.util.List;

import org.springframework.stereotype.Service;

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
 * Service layer for the MenuItem ↔ ModifierGroup assignment lifecycle.
 * <p>
 * The assignment is intentionally separated from {@link MenuItemService}
 * and {@link ModifierGroupService} so the existing Menu / MenuItem /
 * ModifierGroup aggregates stay untouched. A {@link ModifierGroup} attached
 * to a {@link MenuItem} inherits the group's own {@code minSelections} /
 * {@code maxSelections}; per-item overrides are not modelled yet.
 * </p>
 * <p>
 * Duplicate assignments of the same (item, group) pair are rejected here
 * via an existence check and again at the database level by the
 * {@code uk_menu_item_modifier_group} unique constraint.
 * </p>
 */
@Service
public class MenuItemModifierGroupService {

    private final MenuItemRepository menuItemRepo;
    private final ModifierGroupRepository modifierGroupRepo;
    private final MenuItemModifierGroupAssignmentRepository assignmentRepo;

    /**
     * Constructor for MenuItemModifierGroupService.
     * @param menuItemRepo the repository for menu items
     * @param modifierGroupRepo the repository for modifier groups
     * @param assignmentRepo the repository for menu item ↔ modifier group assignments
     */
    public MenuItemModifierGroupService(MenuItemRepository menuItemRepo,
                                        ModifierGroupRepository modifierGroupRepo,
                                        MenuItemModifierGroupAssignmentRepository assignmentRepo) {
        this.menuItemRepo = menuItemRepo;
        this.modifierGroupRepo = modifierGroupRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /**
     * Return the modifier group assignments attached to a menu item,
     * ordered by display position.
     * @param menuItemId the menu item ID
     * @return the ordered assignments (empty when the item has none)
     * @throws MenuItemNotFoundException if no menu item exists with the ID
     */
    public List<MenuItemModifierGroupAssignment> listAssignments(Long menuItemId) {
        if (!menuItemRepo.existsById(menuItemId)) {
            throw new MenuItemNotFoundException(menuItemId);
        }
        return assignmentRepo.findByMenuItemIdOrderByDisplayOrder(menuItemId);
    }

    /**
     * Assign a modifier group to a menu item at a specific display position.
     * @param menuItemId the menu item ID
     * @param modifierGroupId the modifier group ID
     * @param displayOrder the position of the modifier group within the item
     * @return the saved assignment
     * @throws MenuItemNotFoundException if the menu item does not exist
     * @throws ModifierGroupNotFoundException if the modifier group does not exist
     * @throws MenuValidationException if the pair is already assigned
     */
    public MenuItemModifierGroupAssignment assign(Long menuItemId, Long modifierGroupId, int displayOrder) {
        MenuItem item = menuItemRepo.findById(menuItemId)
                .orElseThrow(() -> new MenuItemNotFoundException(menuItemId));
        ModifierGroup group = modifierGroupRepo.findById(modifierGroupId)
                .orElseThrow(() -> new ModifierGroupNotFoundException(modifierGroupId));
        if (assignmentRepo.existsByMenuItemIdAndModifierGroupId(menuItemId, modifierGroupId)) {
            throw new MenuValidationException(
                    "Modifier group already assigned to menu item: menuItemId="
                            + menuItemId + ", modifierGroupId=" + modifierGroupId);
        }
        return assignmentRepo.save(new MenuItemModifierGroupAssignment(item, group, displayOrder));
    }

    /**
     * Remove a specific assignment between a menu item and a modifier group.
     * Neither the menu item nor the modifier group is deleted. A missing
     * pair is reported as 404 via {@link ModifierGroupNotFoundException}
     * because there is no assignment to remove.
     * @param menuItemId the menu item ID
     * @param modifierGroupId the modifier group ID
     * @throws MenuItemNotFoundException if the menu item does not exist
     * @throws ModifierGroupNotFoundException if the assignment does not exist
     */
    public void unassign(Long menuItemId, Long modifierGroupId) {
        if (!menuItemRepo.existsById(menuItemId)) {
            throw new MenuItemNotFoundException(menuItemId);
        }
        MenuItemModifierGroupAssignment assignment = assignmentRepo
                .findByMenuItemIdAndModifierGroupId(menuItemId, modifierGroupId)
                .orElseThrow(() -> new ModifierGroupNotFoundException(modifierGroupId));
        assignmentRepo.delete(assignment);
    }
}
