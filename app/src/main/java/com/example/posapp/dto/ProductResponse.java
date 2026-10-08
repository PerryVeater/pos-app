package com.example.posapp.dto;

import java.math.BigDecimal;

import com.example.posapp.entity.Category;
import com.example.posapp.entity.Product;

/**
 * API representation of a product returned by the Product endpoints.
 * <p>
 * Keeps the HTTP contract decoupled from the JPA {@code Product} entity:
 * JSON field names remain {@code id}, {@code name}, {@code sku},
 * {@code price}, {@code active}, {@code categoryId}, and
 * {@code categoryName}.
 * </p>
 *
 * @param id the product ID
 * @param name the product name
 * @param sku the unique product identifier used by POS systems
 * @param price the product price
 * @param active whether the product is available for sale
 * @param categoryId the ID of the product's category, or null if uncategorized
 * @param categoryName the name of the product's category, or null if uncategorized
 */
public record ProductResponse(Long id, String name, String sku, BigDecimal price, boolean active,
        Long categoryId, String categoryName) {

    /**
     * Map a {@link Product} entity to its API representation.
     * @param product the entity to map
     * @return the API representation of the product
     */
    public static ProductResponse from(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(product.getId(), product.getName(), product.getSku(),
                product.getPrice(), product.isActive(),
                category == null ? null : category.getId(),
                category == null ? null : category.getName());
    }
}
