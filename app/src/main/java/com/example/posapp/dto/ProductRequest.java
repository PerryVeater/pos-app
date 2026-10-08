package com.example.posapp.dto;

import java.math.BigDecimal;

import com.example.posapp.entity.Product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a product over HTTP.
 * <p>
 * Carries the Jakarta Bean Validation constraints enforced at the API
 * boundary: the name must be provided and not blank, the SKU must be
 * provided, not blank, and at most 64 characters, and the price must be
 * provided and non-negative (zero is a valid price).
 * </p>
 * <p>
 * The SKU is the stable product identifier used by POS systems and is always
 * supplied by the caller - it is never auto-generated. {@code active} is
 * optional: omitting it makes the product available for sale, both when
 * creating and under the full-replacement semantics of update.
 * </p>
 *
 * @param name the product name
 * @param sku the caller-supplied unique product identifier
 * @param price the product price
 * @param active whether the product is available for sale (defaults to true)
 */
public record ProductRequest(
        @NotBlank String name,
        @NotBlank @Size(max = 64) String sku,
        @NotNull @PositiveOrZero BigDecimal price,
        Boolean active) {

    /**
     * Map the request to a transient {@link Product} entity for the service layer.
     * @return a new product with the request values and no ID
     */
    public Product toEntity() {
        return new Product(name, sku, price, active == null || active);
    }
}
