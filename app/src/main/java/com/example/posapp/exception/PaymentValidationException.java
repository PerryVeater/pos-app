package com.example.posapp.exception;

/**
 * Thrown when a payment business rule is violated.
 * <p>
 * This exception is used by the payment service to signal that a payment
 * creation or update request violates business rules such as invalid amount,
 * missing order, or cancelled order.
 * </p>
 */
public class PaymentValidationException extends RuntimeException {

    /**
     * Constructor with a detail message.
     * @param message the detail message
     */
    public PaymentValidationException(String message) {
        super(message);
    }
}
