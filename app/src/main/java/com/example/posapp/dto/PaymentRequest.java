package com.example.posapp.dto;

import java.math.BigDecimal;

import com.example.posapp.entity.PaymentMethod;

/**
 * Request DTO for creating a payment.
 * <p>
 * Accepts the order ID, payment amount, and payment method. The service layer
 * validates that the order exists, is not cancelled, and the amount is positive.
 * </p>
 *
 * @param orderId the ID of the order to pay for
 * @param amount the payment amount; must be positive
 * @param method the payment method
 */
public record PaymentRequest(Long orderId, BigDecimal amount, PaymentMethod method) {}
