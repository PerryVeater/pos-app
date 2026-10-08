package com.example.posapp.dto;

import java.util.List;

/**
 * Request DTO for creating an order.
 * <p>
 * Accepts a list of line items, each referencing a product by ID and a quantity.
 * The service layer validates that the list is non-empty, each product exists
 * and is active, and each quantity is positive.
 * </p>
 *
 * @param lines the line items to include in the order
 */
public record OrderRequest(List<OrderLineRequest> lines) {

    /**
     * A single line item in an order request.
     *
     * @param productId the ID of the product to order
     * @param quantity the number of units to order; must be positive
     */
    public record OrderLineRequest(Long productId, int quantity) {}
}
