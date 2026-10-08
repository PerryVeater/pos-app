package com.example.posapp.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.example.posapp.entity.Category;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.exception.MenuItemNotFoundException;
import com.example.posapp.repository.CategoryRepository;
import com.example.posapp.repository.MenuItemRepository;

/**
 * Service layer responsible for managing {@link MenuItem} entities in the POS application.
 * <p>
 * This class encapsulates business logic for menu item management, including validation
 * and interaction with the {@link MenuItemRepository} and {@link CategoryRepository}.
 * It provides CRUD operations (create, read, update, delete) and enforces rules such
 * as preventing negative prices and rejecting category references that do not exist.
 * </p>
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Called by {@link com.example.posapp.controller.MenuItemController} to process
 *       client requests.</li>
 *   <li>Ensures data integrity before interacting with the database.</li>
 * </ul>
 * </p>
 * 
 * @see com.example.posapp.entity.MenuItem
 * @see com.example.posapp.repository.MenuItemRepository
 */
@Service
public class MenuItemService {

    private final MenuItemRepository menuItemRepo;

    private final CategoryRepository categoryRepo;
    
    /**
     * Constructor for MenuItemService.
     * @param menuItemRepo The repository for {@link MenuItem}s.
     * @param categoryRepo The repository for {@link Category}s.
     */
    public MenuItemService(MenuItemRepository menuItemRepo, CategoryRepository categoryRepo) {
        this.menuItemRepo = menuItemRepo;
        this.categoryRepo = categoryRepo;
    }

   /**
     * Create a new {@link MenuItem}.
     * Validates that the price is present and non-negative and that the SKU
     * is present and not already used by another menu item before saving. A
     * category reference, when given, must resolve to an existing category.
     *
     * @param menuItem the MenuItem to create
     * @param categoryId the ID of the menu item's category, or {@code null} for none
     * @return the saved MenuItem
     * @throws IllegalArgumentException if the menu item price is missing or negative,
     *         the SKU is missing or too long, the SKU is already in use, or the
     *         category ID does not reference an existing category
     */
    public MenuItem createMenuItem(MenuItem menuItem, Long categoryId) {
        validatePrice(menuItem.getPrice());
        validateSku(menuItem.getSku());
        if (menuItemRepo.existsBySku(menuItem.getSku())) {
            throw new IllegalArgumentException("SKU already exists: " + menuItem.getSku());
        }
        menuItem.setCategory(resolveCategory(categoryId));
        return menuItemRepo.save(menuItem);
    }

    /**
     * Update an existing {@link MenuItem}.
     * Validates that the price is present and non-negative and that the
     * {@link MenuItem} exists before updating. The SKU is rejected when another
     * menu item already uses it; keeping the menu item's own SKU is allowed. The
     * category is fully replaced: a valid {@code categoryId} assigns that
     * category, and {@code null} leaves the menu item uncategorized.
     *
     * @param id the ID of the {@link MenuItem} to update
     * @param updatedMenuItem the updated {@link MenuItem} data
     * @param categoryId the ID of the menu item's new category, or {@code null} for none
     * @return the updated {@link MenuItem}
     * @throws IllegalArgumentException if the menu item price is missing or negative,
     *         the SKU is missing or too long, the SKU is used by another menu item,
     *         or the category ID does not reference an existing category
     * @throws MenuItemNotFoundException if the {@link MenuItem} does not exist
     */
    public MenuItem updateMenuItem(Long id, MenuItem updatedMenuItem, Long categoryId) {
        validatePrice(updatedMenuItem.getPrice());
        validateSku(updatedMenuItem.getSku());
        return menuItemRepo.findById(id)
            .map(existing -> {
                if (menuItemRepo.existsBySkuAndIdNot(updatedMenuItem.getSku(), id)) {
                    throw new IllegalArgumentException("SKU already exists: " + updatedMenuItem.getSku());
                }
                existing.setName(updatedMenuItem.getName());
                existing.setSku(updatedMenuItem.getSku());
                existing.setPrice(updatedMenuItem.getPrice());
                existing.setActive(updatedMenuItem.isActive());
                existing.setCategory(resolveCategory(categoryId));
                return menuItemRepo.save(existing);
            })
            .orElseThrow(() -> new MenuItemNotFoundException(id));
    }
    
    /**
     * Delete a {@link MenuItem} by ID.
     * Does not throw an exception if the {@link MenuItem} does not exist.
     *
     * @param id the ID of the {@link MenuItem} to delete
     */
    public void deleteMenuItem(Long id) {
        menuItemRepo.deleteById(id);
    }    

    /**
     * Retrieve all {@link MenuItem}s from the repository.
     *
     * @return a list of all {@link MenuItem}s
     */
    public List<MenuItem> getAllMenuItems() {
        return menuItemRepo.findAll();
    }

    /**
     * Retrieve a {@link MenuItem} by its ID.
     * Returns Optional.empty() if the {@link MenuItem} does not exist.
     *
     * @param id the ID of the {@link MenuItem} to retrieve
     * @return an Optional containing the {@link MenuItem} if found, or empty if not
     * @throws IllegalArgumentException if the ID is null
     */
    public Optional<MenuItem> getMenuItemById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return menuItemRepo.findById(id);
    }

    /**
     * Reject missing or negative prices for any write operation.
     * @param price the price to validate
     * @throws IllegalArgumentException if the price is missing or negative
     */
    private static void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Price must be provided");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
    }

    /**
     * Reject missing, blank, or over-long SKUs for any write operation.
     * Mirrors the API boundary rules and the VARCHAR(64) column so direct
     * service callers cannot bypass them.
     * @param sku the SKU to validate
     * @throws IllegalArgumentException if the SKU is missing, blank, or longer
     *         than 64 characters
     */
    private static void validateSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU must be provided");
        }
        if (sku.length() > 64) {
            throw new IllegalArgumentException("SKU must be at most 64 characters");
        }
    }

    /**
     * Resolve an optional category reference to a managed {@link Category}.
     * @param categoryId the requested category ID, or {@code null} for none
     * @return the matching category, or {@code null} when no category was requested
     * @throws IllegalArgumentException if the ID does not reference an existing
     *         category
     */
    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepo.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
    }
}
