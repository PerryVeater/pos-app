package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.MenuGroup;
import com.example.posapp.exception.MenuGroupNotFoundException;
import com.example.posapp.exception.MenuValidationException;
import com.example.posapp.repository.MenuGroupAssignmentRepository;
import com.example.posapp.repository.MenuGroupRepository;

/**
 * Service layer for {@link MenuGroup} entities.
 * <p>
 * Menu groups are reusable across menus; this service enforces the
 * name-required / name-unique rules and refuses to delete a group that is
 * still assigned to a menu, so callers must first unassign it.
 * </p>
 */
@Service
public class MenuGroupService {

    private final MenuGroupRepository menuGroupRepo;
    private final MenuGroupAssignmentRepository assignmentRepo;

    /**
     * Constructor for MenuGroupService.
     * @param menuGroupRepo the repository for menu groups
     * @param assignmentRepo the repository for menu ↔ menu group assignments
     */
    public MenuGroupService(MenuGroupRepository menuGroupRepo,
                            MenuGroupAssignmentRepository assignmentRepo) {
        this.menuGroupRepo = menuGroupRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /**
     * Create a new menu group after validating its name.
     * @param group the transient group to save
     * @return the saved group
     * @throws MenuValidationException if the name is blank or already used
     */
    public MenuGroup createMenuGroup(MenuGroup group) {
        validateName(group.getName());
        if (menuGroupRepo.existsByName(group.getName())) {
            throw new MenuValidationException("Menu group name already exists: " + group.getName());
        }
        return menuGroupRepo.save(group);
    }

    /**
     * Update an existing menu group. The name is validated for uniqueness
     * against other groups; keeping the group's own name is allowed.
     * @param id the menu group ID
     * @param updated the replacement values
     * @return the updated group
     * @throws MenuGroupNotFoundException if no group exists with the ID
     * @throws MenuValidationException if the name is blank or used by another group
     */
    public MenuGroup updateMenuGroup(Long id, MenuGroup updated) {
        validateName(updated.getName());
        return menuGroupRepo.findById(id)
                .map(existing -> {
                    if (menuGroupRepo.existsByNameAndIdNot(updated.getName(), id)) {
                        throw new MenuValidationException(
                                "Menu group name already exists: " + updated.getName());
                    }
                    existing.setName(updated.getName());
                    existing.setActive(updated.isActive());
                    return menuGroupRepo.save(existing);
                })
                .orElseThrow(() -> new MenuGroupNotFoundException(id));
    }

    /**
     * Delete a menu group by ID. Refuses to delete a group that is still
     * assigned to at least one menu so callers must unassign it first.
     * @param id the menu group ID
     * @throws MenuGroupNotFoundException if no group exists with the ID
     * @throws MenuValidationException if the group is still assigned to a menu
     */
    public void deleteMenuGroup(Long id) {
        if (!menuGroupRepo.existsById(id)) {
            throw new MenuGroupNotFoundException(id);
        }
        if (assignmentRepo.countByMenuGroupId(id) > 0) {
            throw new MenuValidationException(
                    "Cannot delete menu group still assigned to a menu: " + id);
        }
        menuGroupRepo.deleteById(id);
    }

    /**
     * Retrieve every menu group.
     * @return the list of menu groups
     */
    public List<MenuGroup> getAllMenuGroups() {
        return menuGroupRepo.findAll();
    }

    /**
     * Retrieve a menu group by ID.
     * @param id the menu group ID
     * @return the Optional containing the group if found
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<MenuGroup> getMenuGroupById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return menuGroupRepo.findById(id);
    }

    /**
     * Reject blank or missing names for any write operation.
     * @param name the name to validate
     * @throws MenuValidationException if the name is missing or blank
     */
    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new MenuValidationException("Name must be provided");
        }
    }
}
