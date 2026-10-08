package com.example.posapp.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

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
 * Service layer for {@link Menu} entities and their menu group assignments.
 * <p>
 * Encapsulates the business rules: names are required and unique; menu
 * group assignment requires both the menu and the group to exist and the
 * pair to be unique; removing a group is rejected while the group is still
 * assigned to a menu.
 * </p>
 */
@Service
public class MenuService {

    private final MenuRepository menuRepo;
    private final MenuGroupRepository menuGroupRepo;
    private final MenuGroupAssignmentRepository assignmentRepo;

    /**
     * Constructor for MenuService.
     * @param menuRepo the repository for menus
     * @param menuGroupRepo the repository for menu groups
     * @param assignmentRepo the repository for menu ↔ menu group assignments
     */
    public MenuService(MenuRepository menuRepo,
                       MenuGroupRepository menuGroupRepo,
                       MenuGroupAssignmentRepository assignmentRepo) {
        this.menuRepo = menuRepo;
        this.menuGroupRepo = menuGroupRepo;
        this.assignmentRepo = assignmentRepo;
    }

    /**
     * Create a new menu after validating its name.
     * @param menu the transient menu to save
     * @return the saved menu
     * @throws MenuValidationException if the name is blank or already used
     */
    public Menu createMenu(Menu menu) {
        validateName(menu.getName());
        if (menuRepo.existsByName(menu.getName())) {
            throw new MenuValidationException("Menu name already exists: " + menu.getName());
        }
        return menuRepo.save(menu);
    }

    /**
     * Update an existing menu. The name is validated for uniqueness against
     * other menus; keeping the menu's own name is allowed.
     * @param id the menu ID
     * @param updated the replacement values
     * @return the updated menu
     * @throws MenuNotFoundException if no menu exists with the ID
     * @throws MenuValidationException if the name is blank or used by another menu
     */
    public Menu updateMenu(Long id, Menu updated) {
        validateName(updated.getName());
        return menuRepo.findById(id)
                .map(existing -> {
                    if (menuRepo.existsByNameAndIdNot(updated.getName(), id)) {
                        throw new MenuValidationException("Menu name already exists: " + updated.getName());
                    }
                    existing.setName(updated.getName());
                    existing.setActive(updated.isActive());
                    return menuRepo.save(existing);
                })
                .orElseThrow(() -> new MenuNotFoundException(id));
    }

    /**
     * Delete a menu by ID. Any assignments attached to it are removed
     * through the JPA cascade on {@link Menu#getAssignments()}.
     * @param id the menu ID
     */
    public void deleteMenu(Long id) {
        menuRepo.deleteById(id);
    }

    /**
     * Retrieve every menu.
     * @return the list of menus
     */
    public List<Menu> getAllMenus() {
        return menuRepo.findAll();
    }

    /**
     * Retrieve a menu by ID.
     * @param id the menu ID
     * @return the Optional containing the menu if found
     * @throws IllegalArgumentException if {@code id} is null
     */
    public Optional<Menu> getMenuById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return menuRepo.findById(id);
    }

    /**
     * Return the assignments attached to a menu, ordered by display position.
     * @param menuId the menu ID
     * @return the ordered assignments (empty when the menu has none)
     * @throws MenuNotFoundException if no menu exists with the ID
     */
    public List<MenuGroupAssignment> listAssignments(Long menuId) {
        if (!menuRepo.existsById(menuId)) {
            throw new MenuNotFoundException(menuId);
        }
        return assignmentRepo.findByMenuIdOrderByDisplayOrder(menuId);
    }

    /**
     * Assign a menu group to a menu at a specific display position.
     * @param menuId the menu ID
     * @param menuGroupId the menu group ID
     * @param displayOrder the position of the group within the menu
     * @return the saved assignment
     * @throws MenuNotFoundException if the menu does not exist
     * @throws MenuGroupNotFoundException if the menu group does not exist
     * @throws MenuValidationException if the group is already assigned to the menu
     */
    public MenuGroupAssignment assignGroup(Long menuId, Long menuGroupId, int displayOrder) {
        Menu menu = menuRepo.findById(menuId)
                .orElseThrow(() -> new MenuNotFoundException(menuId));
        MenuGroup group = menuGroupRepo.findById(menuGroupId)
                .orElseThrow(() -> new MenuGroupNotFoundException(menuGroupId));
        if (assignmentRepo.existsByMenuIdAndMenuGroupId(menuId, menuGroupId)) {
            throw new MenuValidationException(
                    "Menu group already assigned to menu: menuId=" + menuId
                            + ", menuGroupId=" + menuGroupId);
        }
        return assignmentRepo.save(new MenuGroupAssignment(menu, group, displayOrder));
    }

    /**
     * Remove a specific assignment between a menu and a menu group. Missing
     * pair is reported as 404 via {@link MenuGroupNotFoundException} because
     * there is no assignment to remove.
     * @param menuId the menu ID
     * @param menuGroupId the menu group ID
     * @throws MenuNotFoundException if the menu does not exist
     * @throws MenuGroupNotFoundException if the assignment does not exist
     */
    public void unassignGroup(Long menuId, Long menuGroupId) {
        if (!menuRepo.existsById(menuId)) {
            throw new MenuNotFoundException(menuId);
        }
        MenuGroupAssignment assignment = assignmentRepo
                .findByMenuIdAndMenuGroupId(menuId, menuGroupId)
                .orElseThrow(() -> new MenuGroupNotFoundException(menuGroupId));
        assignmentRepo.delete(assignment);
    }

    /**
     * Reject blank or missing names for any write operation, mirroring the
     * API boundary rules.
     * @param name the name to validate
     * @throws MenuValidationException if the name is missing or blank
     */
    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new MenuValidationException("Name must be provided");
        }
    }
}
