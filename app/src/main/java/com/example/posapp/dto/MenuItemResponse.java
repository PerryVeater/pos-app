package com.example.posapp.dto;

import java.math.BigDecimal;

import com.example.posapp.entity.Category;
import com.example.posapp.entity.MenuItem;

/**
 * API representation of a menu item returned by the menu item endpoints.
 * <p>
 * Keeps the HTTP contract decoupled from the JPA {@code MenuItem} entity:
 * JSON field names remain {@code id}, {@code name}, {@code sku},
 * {@code price}, {@code active}, {@code categoryId}, and
 * {@code categoryName}.
 * </p>
 *
 * @param id the menu item ID
 * @param name the menu item name
 * @param sku the unique menu item identifier used by POS systems
 * @param price the menu item price
 * @param active whether the menu item is available for sale
 * @param categoryId the ID of the menu item's category, or null if uncategorized
 * @param categoryName the name of the menu item's category, or null if uncategorized
 */
public record MenuItemResponse(Long id, String name, String sku, BigDecimal price, boolean active,
        Long categoryId, String categoryName) {

    /**
     * Map a {@link MenuItem} entity to its API representation.
     * @param menuItem the entity to map
     * @return the API representation of the menu item
     */
    public static MenuItemResponse from(MenuItem menuItem) {
        Category category = menuItem.getCategory();
        return new MenuItemResponse(menuItem.getId(), menuItem.getName(), menuItem.getSku(),
                menuItem.getPrice(), menuItem.isActive(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName());
    }
}
