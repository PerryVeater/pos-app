package com.example.posapp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.MenuItemAssignmentRequest;
import com.example.posapp.dto.MenuItemAssignmentResponse;
import com.example.posapp.dto.MenuGroupRequest;
import com.example.posapp.dto.MenuGroupResponse;
import com.example.posapp.entity.MenuGroup;
import com.example.posapp.exception.MenuGroupNotFoundException;
import com.example.posapp.service.MenuGroupService;

import jakarta.validation.Valid;

/**
 * REST controller for menu group operations.
 * <p>
 * Menu groups are independent of menus: the same group can be reused
 * across many menus through the assignment sub-resource on
 * {@link MenuController}.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/menu-groups")
public class MenuGroupController {

    private final MenuGroupService menuGroupService;

    /**
     * Constructor for MenuGroupController.
     * @param menuGroupService the service for menu group operations
     */
    public MenuGroupController(MenuGroupService menuGroupService) {
        this.menuGroupService = menuGroupService;
    }

    /**
     * List every menu group.
     * @return all menu groups as API responses
     */
    @GetMapping
    public List<MenuGroupResponse> getMenuGroups() {
        return menuGroupService.getAllMenuGroups().stream()
                .map(MenuGroupResponse::from)
                .toList();
    }

    /**
     * Get a menu group by ID.
     * @param id the menu group ID
     * @return the menu group
     * @throws MenuGroupNotFoundException if no group exists with the ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<MenuGroupResponse> getMenuGroup(@PathVariable Long id) {
        MenuGroup group = menuGroupService.getMenuGroupById(id)
                .orElseThrow(() -> new MenuGroupNotFoundException(id));
        return ResponseEntity.ok(MenuGroupResponse.from(group));
    }

    /**
     * Create a new menu group.
     * @param request the validated menu group payload
     * @return the created menu group
     */
    @PostMapping
    public ResponseEntity<MenuGroupResponse> createMenuGroup(
            @Valid @RequestBody MenuGroupRequest request) {
        MenuGroup saved = menuGroupService.createMenuGroup(request.toEntity());
        return ResponseEntity.status(201).body(MenuGroupResponse.from(saved));
    }

    /**
     * Update an existing menu group's name and active flag.
     * @param id the menu group ID
     * @param request the replacement values
     * @return the updated menu group
     */
    @PutMapping("/{id}")
    public MenuGroupResponse updateMenuGroup(
            @PathVariable Long id,
            @Valid @RequestBody MenuGroupRequest request) {
        return MenuGroupResponse.from(menuGroupService.updateMenuGroup(id, request.toEntity()));
    }

    /**
     * Delete a menu group by ID. Groups still assigned to a menu are
     * rejected with 400.
     * @param id the menu group ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenuGroup(@PathVariable Long id) {
        menuGroupService.deleteMenuGroup(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * List the menu items assigned to a menu group, ordered by display position.
     * @param id the menu group ID
     * @return the ordered assignments
     */
    @GetMapping("/{id}/menu-items")
    public List<MenuItemAssignmentResponse> listMenuItems(@PathVariable Long id) {
        return menuGroupService.listItemAssignments(id).stream()
                .map(MenuItemAssignmentResponse::from)
                .toList();
    }

    /**
     * Assign a menu item to a menu group at the given display order.
     * @param id the menu group ID
     * @param request the assignment payload (menuItemId and displayOrder)
     * @return the created assignment
     */
    @PostMapping("/{id}/menu-items")
    public ResponseEntity<MenuItemAssignmentResponse> assignMenuItem(
            @PathVariable Long id,
            @Valid @RequestBody MenuItemAssignmentRequest request) {
        return ResponseEntity.status(201).body(MenuItemAssignmentResponse.from(
                menuGroupService.assignItem(id, request.menuItemId(), request.displayOrder())));
    }

    /**
     * Remove a menu item assignment from a menu group.
     * @param id the menu group ID
     * @param menuItemId the menu item ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}/menu-items/{menuItemId}")
    public ResponseEntity<Void> unassignMenuItem(
            @PathVariable Long id,
            @PathVariable Long menuItemId) {
        menuGroupService.unassignItem(id, menuItemId);
        return ResponseEntity.noContent().build();
    }
}
