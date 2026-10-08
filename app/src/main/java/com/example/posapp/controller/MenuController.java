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

import com.example.posapp.dto.MenuGroupAssignmentRequest;
import com.example.posapp.dto.MenuGroupAssignmentResponse;
import com.example.posapp.dto.MenuRequest;
import com.example.posapp.dto.MenuResponse;
import com.example.posapp.entity.Menu;
import com.example.posapp.exception.MenuNotFoundException;
import com.example.posapp.service.MenuService;

import jakarta.validation.Valid;

/**
 * REST controller for menu operations, including the sub-resource for
 * assigning and unassigning {@link com.example.posapp.entity.MenuGroup}s.
 * <p>
 * Business rules are enforced by {@link MenuService}. The HTTP contract is
 * decoupled from the JPA model via {@link MenuRequest} and
 * {@link MenuResponse}.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuService menuService;

    /**
     * Constructor for MenuController.
     * @param menuService the service for menu operations
     */
    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * List all menus. Assignments are returned empty in the collection view
     * to avoid an N+1 query; use {@code GET /api/v1/menus/{id}} for the
     * full detail.
     * @return every menu as an API response
     */
    @GetMapping
    public List<MenuResponse> getMenus() {
        return menuService.getAllMenus().stream()
                .map(menu -> MenuResponse.from(menu, List.of()))
                .toList();
    }

    /**
     * Get a menu by ID, including its assigned menu groups in display order.
     * @param id the menu ID
     * @return the menu with its assignments
     * @throws MenuNotFoundException if the menu does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<MenuResponse> getMenu(@PathVariable Long id) {
        Menu menu = menuService.getMenuById(id)
                .orElseThrow(() -> new MenuNotFoundException(id));
        return ResponseEntity.ok(MenuResponse.from(menu, menuService.listAssignments(id)));
    }

    /**
     * Create a new menu.
     * @param request the validated menu creation payload
     * @return the created menu (empty groups list)
     */
    @PostMapping
    public ResponseEntity<MenuResponse> createMenu(@Valid @RequestBody MenuRequest request) {
        Menu saved = menuService.createMenu(request.toEntity());
        return ResponseEntity.status(201).body(MenuResponse.from(saved, List.of()));
    }

    /**
     * Update an existing menu's name and active flag.
     * @param id the menu ID
     * @param request the replacement values
     * @return the updated menu
     */
    @PutMapping("/{id}")
    public MenuResponse updateMenu(@PathVariable Long id, @Valid @RequestBody MenuRequest request) {
        Menu updated = menuService.updateMenu(id, request.toEntity());
        return MenuResponse.from(updated, menuService.listAssignments(id));
    }

    /**
     * Delete a menu by ID. Attached assignments are removed through the JPA
     * cascade; the referenced menu groups stay intact.
     * @param id the menu ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenu(@PathVariable Long id) {
        menuService.deleteMenu(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * List the menu groups assigned to a menu, ordered by display position.
     * @param id the menu ID
     * @return the ordered assignments
     */
    @GetMapping("/{id}/menu-groups")
    public List<MenuGroupAssignmentResponse> listMenuGroups(@PathVariable Long id) {
        return menuService.listAssignments(id).stream()
                .map(MenuGroupAssignmentResponse::from)
                .toList();
    }

    /**
     * Assign a menu group to a menu with the given display order.
     * @param id the menu ID
     * @param request the assignment payload (menuGroupId and displayOrder)
     * @return the created assignment
     */
    @PostMapping("/{id}/menu-groups")
    public ResponseEntity<MenuGroupAssignmentResponse> assignMenuGroup(
            @PathVariable Long id,
            @Valid @RequestBody MenuGroupAssignmentRequest request) {
        return ResponseEntity.status(201).body(MenuGroupAssignmentResponse.from(
                menuService.assignGroup(id, request.menuGroupId(), request.displayOrder())));
    }

    /**
     * Unassign a menu group from a menu.
     * @param id the menu ID
     * @param menuGroupId the menu group ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}/menu-groups/{menuGroupId}")
    public ResponseEntity<Void> unassignMenuGroup(
            @PathVariable Long id,
            @PathVariable Long menuGroupId) {
        menuService.unassignGroup(id, menuGroupId);
        return ResponseEntity.noContent().build();
    }
}
