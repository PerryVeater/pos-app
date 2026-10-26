package com.example.posapp.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Order;

/**
 * Response DTO for an order.
 * <p>
 * Exposes the order ID, status, dining option, creation timestamp, store and
 * employee attribution, line items, and computed total. The total is calculated
 * by the {@link Order#getTotal()} method and represents the sum of all line
 * subtotals. Store and employee attribution are always present: the POS order
 * lifecycle is the only way an order is created and derives them from the
 * authenticated session.
 * </p>
 *
 * @param id the order ID
 * @param status the order status (PENDING, CONFIRMED, COMPLETED, CANCELLED)
 * @param diningOption how the order will be consumed (DINE_IN, TAKEOUT, ONLINE)
 * @param createdAt the instant when the order was created, rendered in UTC
 *        (ISO-8601 with a {@code Z} offset)
 * @param storeId the owning store ID, derived from the authenticated session
 * @param employeeId the creating employee ID, derived from the authenticated session
 * @param lines the line items in the order
 * @param total the computed order total
 */
public record OrderResponse(
        Long id,
        String status,
        DiningOption diningOption,
        Instant createdAt,
        Long storeId,
        Long employeeId,
        List<OrderLineResponse> lines,
        BigDecimal total) {

    /**
     * A single line item in an order response.
     *
     * @param productId the ID of the product
     * @param quantity the number of units ordered
     * @param unitPrice the price per unit at order-creation time
     * @param lineSubtotal the line total (quantity × unitPrice)
     */
    public record OrderLineResponse(
            Long productId,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineSubtotal) {}

    /**
     * Build an OrderResponse from an Order entity.
     * @param order the order entity
     * @return the response DTO
     */
    public static OrderResponse from(Order order) {
        List<OrderLineResponse> lineResponses = order.getLines().stream()
                .map(line -> new OrderLineResponse(
                        line.getMenuItem().getId(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()))))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus().name(),
                order.getDiningOption(),
                order.getCreatedAt(),
                order.getStore().getId(),
                order.getEmployee().getId(),
                lineResponses,
                order.getTotal());
    }
}
