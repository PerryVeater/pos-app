package com.example.posapp.dto;

import com.example.posapp.entity.OrderStatus;

/**
 * Request DTO for updating an order's status.
 * <p>
 * Accepts a single field: the target {@link OrderStatus}. The service layer
 * validates that the transition is allowed.
 * </p>
 *
 * @param status the target status for the order
 */
public record OrderStatusUpdateRequest(OrderStatus status) {}
