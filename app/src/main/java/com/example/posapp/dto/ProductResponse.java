package com.example.posapp.dto;

import java.math.BigDecimal;

import com.example.posapp.entity.Product;

/**
 * API representation of a product returned by the Product endpoints.
 * <p>
 * Keeps the HTTP contract decoupled from the JPA {@code Product} entity:
 * JSON field names remain {@code id}, {@code name}, and {@code price}.
 * </p>
 *
 * @param id the product ID
 * @param name the product name
 * @param price the product price
 */
public record ProductResponse(Long id, String name, BigDecimal price) {

    /**
     * Map a {@link Product} entity to its API representation.
     * @param product the entity to map
     * @return the API representation of the product
     */
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getPrice());
    }
}
