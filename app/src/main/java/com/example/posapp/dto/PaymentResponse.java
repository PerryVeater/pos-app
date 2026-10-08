package com.example.posapp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;

/**
 * Response DTO for payment data.
 * <p>
 * Exposes the payment id, order id, amount, method, status, and creation
 * timestamp. Does not expose the JPA entity directly.
 * </p>
 *
 * @param id the payment ID
 * @param orderId the ID of the order this payment is for
 * @param amount the payment amount
 * @param method the payment method
 * @param status the payment status
 * @param createdAt the creation timestamp
 */
public record PaymentResponse(
        Long id,
        Long orderId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        LocalDateTime createdAt) {

    /**
     * Factory method to create a PaymentResponse from a Payment entity.
     * @param payment the payment entity
     * @return the response DTO
     */
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getCreatedAt());
    }
}
