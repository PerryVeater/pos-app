package com.example.posapp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.MenuItemModifierGroupAssignmentRequest;
import com.example.posapp.dto.MenuItemModifierGroupAssignmentResponse;
import com.example.posapp.service.MenuItemModifierGroupService;

import jakarta.validation.Valid;

/**
 * REST controller for the {@code /api/v1/menu-items/{id}/modifier-groups}
 * sub-resource, which manages ModifierGroup assignments on a MenuItem.
 * <p>
 * Kept separate from the legacy {@code /products} {@link MenuItemController}
 * so the existing MenuItem API stays untouched, per the project convention
 * of exposing new assignment layers through dedicated sub-resource
 * controllers (see {@link MenuGroupController} for the
 * {@code /menu-groups/{id}/menu-items} pattern).
 * </p>
 */
@RestController
@RequestMapping("/api/v1/menu-items")
public class MenuItemModifierGroupController {

    private final MenuItemModifierGroupService assignmentService;

    /**
     * Constructor for MenuItemModifierGroupController.
     * @param assignmentService the service for menu item ↔ modifier group assignments
     */
    public MenuItemModifierGroupController(MenuItemModifierGroupService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * List the modifier groups assigned to a menu item, ordered by display position.
     * @param id the menu item ID
     * @return the ordered assignments
     */
    @GetMapping("/{id}/modifier-groups")
    public List<MenuItemModifierGroupAssignmentResponse> listModifierGroups(@PathVariable Long id) {
        return assignmentService.listAssignments(id).stream()
                .map(MenuItemModifierGroupAssignmentResponse::from)
                .toList();
    }

    /**
     * Assign a modifier group to a menu item with the given display order.
     * @param id the menu item ID
     * @param request the assignment payload (modifierGroupId and displayOrder)
     * @return the created assignment
     */
    @PostMapping("/{id}/modifier-groups")
    public ResponseEntity<MenuItemModifierGroupAssignmentResponse> assignModifierGroup(
            @PathVariable Long id,
            @Valid @RequestBody MenuItemModifierGroupAssignmentRequest request) {
        return ResponseEntity.status(201).body(MenuItemModifierGroupAssignmentResponse.from(
                assignmentService.assign(id, request.modifierGroupId(), request.displayOrder())));
    }

    /**
     * Unassign a modifier group from a menu item. Neither the underlying
     * MenuItem nor the ModifierGroup is deleted.
     * @param id the menu item ID
     * @param modifierGroupId the modifier group ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}/modifier-groups/{modifierGroupId}")
    public ResponseEntity<Void> unassignModifierGroup(
            @PathVariable Long id,
            @PathVariable Long modifierGroupId) {
        assignmentService.unassign(id, modifierGroupId);
        return ResponseEntity.noContent().build();
    }
}
