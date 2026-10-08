package com.example.posapp.dto;

import java.math.BigDecimal;

import com.example.posapp.entity.Product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a product over HTTP.
 * <p>
 * Carries the Jakarta Bean Validation constraints enforced at the API
 * boundary: the name must be provided and not blank, the SKU must be
 * provided, not blank, and at most 64 characters, the price must be
 * provided and non-negative (zero is a valid price), and a category
 * reference, when given, must be a positive ID.
 * </p>
 * <p>
 * The SKU is the stable product identifier used by POS systems and is always
 * supplied by the caller - it is never auto-generated. {@code active} and
 * {@code categoryId} are optional: omitting {@code active} makes the product
 * available for sale, and omitting {@code categoryId} leaves it
 * uncategorized, both when creating and under the full-replacement semantics
 * of update. A provided {@code categoryId} must reference an existing
 * category; the service rejects unknown IDs as a client error.
 * </p>
 *
 * @param name the product name
 * @param sku the caller-supplied unique product identifier
 * @param price the product price
 * @param active whether the product is available for sale (defaults to true)
 * @param categoryId the ID of the product's category, or null for none
 */
public record ProductRequest(
        @NotBlank String name,
        @NotBlank @Size(max = 64) String sku,
        @NotNull @PositiveOrZero BigDecimal price,
        Boolean active,
        @Positive Long categoryId) {

    /**
     * Map the request to a transient {@link Product} entity for the service layer.
     * The category is not mapped here: the service resolves {@code categoryId}
     * against the category repository, so the entity stays HTTP-agnostic.
     * @return a new product with the request values and no ID
     */
    public Product toEntity() {
        return new Product(name, sku, price, active == null || active);
    }
}
