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

import com.example.posapp.dto.MenuItemRequest;
import com.example.posapp.dto.MenuItemResponse;
import com.example.posapp.service.MenuItemService;

import jakarta.validation.Valid;

/**
 * Controller class for managing menu items over HTTP.
 * <p>
 * This class is annotated with {@code @RestController} to indicate that it's a REST controller,
 * and mapped to the "/products" endpoint (path preserved for backward compatibility).
 * </p>
 * <p>
 * The HTTP API is decoupled from the persistence model: requests are received
 * as {@link MenuItemRequest} and responses are returned as {@link MenuItemResponse},
 * so the {@code MenuItem} entity is never exposed directly. Typical usage:
 * <ul>
 *   <li>Delegates business logic to {@link com.example.posapp.service.MenuItemService}.</li>
 *   <li>Provides endpoints for CRUD operations on menu items.</li>
 * </ul>
 * </p>
 *
 * @see com.example.posapp.dto.MenuItemRequest
 * @see com.example.posapp.dto.MenuItemResponse
 * @see com.example.posapp.service.MenuItemService
 */
@RestController
@RequestMapping("/products")
public class MenuItemController {

    private final MenuItemService menuItemService;

    /**
     * Constructor for MenuItemController.
     * @param menuItemService the service for menu items.
     */
    public MenuItemController(MenuItemService menuItemService) {
        this.menuItemService = menuItemService;
    }

    /**
     * Get all menu items.
     * @return a list of all menu items as API responses
     */
    @GetMapping
    public List<MenuItemResponse> getMenuItems() {
        return menuItemService.getAllMenuItems().stream()
                .map(MenuItemResponse::from)
                .toList();
    }

    /**
     * Get a menu item by ID.
     * @param id the ID of the menu item
     * @return the menu item with the given ID, or 404 if it does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<MenuItemResponse> getMenuItem(@PathVariable Long id) {
        return menuItemService.getMenuItemById(id)
                .map(MenuItemResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add a new menu item.
     * @param request the validated menu item to add
     * @return the added menu item as an API response
     */
    @PostMapping
    public MenuItemResponse addMenuItem(@Valid @RequestBody MenuItemRequest request) {
        return MenuItemResponse.from(menuItemService.createMenuItem(request.toEntity(), request.categoryId()));
    }

    /**
     * Update a menu item.
     * @param id the ID of the menu item
     * @param request the validated menu item update
     * @return the updated menu item as an API response
     */
    @PutMapping("/{id}")
    public MenuItemResponse updateMenuItem(@PathVariable Long id, @Valid @RequestBody MenuItemRequest request) {
        return MenuItemResponse.from(menuItemService.updateMenuItem(id, request.toEntity(), request.categoryId()));
    }

    /**
     * Delete a menu item.
     * @param id the ID of the menu item
     * @return a response entity with no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMenuItem(@PathVariable Long id) {
        menuItemService.deleteMenuItem(id);
        return ResponseEntity.noContent().build();
    }
}
