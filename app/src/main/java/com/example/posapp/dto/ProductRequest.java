package com.example.posapp.dto;

import java.math.BigDecimal;

import com.example.posapp.entity.Product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Request body for creating or updating a product over HTTP.
 * <p>
 * Carries the Jakarta Bean Validation constraints enforced at the API
 * boundary: the name must be provided and not blank, and the price must be
 * provided and non-negative (zero is a valid price).
 * </p>
 *
 * @param name the product name
 * @param price the product price
 */
public record ProductRequest(
        @NotBlank String name,
        @NotNull @PositiveOrZero BigDecimal price) {

    /**
     * Map the request to a transient {@link Product} entity for the service layer.
     * @return a new product with the request values and no ID
     */
    public Product toEntity() {
        return new Product(name, price);
    }
}
