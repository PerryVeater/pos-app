package com.example.posapp.dto;

import com.example.posapp.entity.PaymentStatus;

/**
 * Request DTO for updating a payment's status.
 * <p>
 * Accepts a single field: the target {@link PaymentStatus}. The service layer
 * validates that the transition is allowed.
 * </p>
 *
 * @param status the target status for the payment
 */
public record PaymentStatusUpdateRequest(PaymentStatus status) {}
