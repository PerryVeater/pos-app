package com.example.posapp.exception;

/**
 * Thrown when a requested payment is not found.
 * <p>
 * This exception is used by the payment service and controller to signal
 * that a payment with the given ID does not exist.
 * </p>
 */
public class PaymentNotFoundException extends RuntimeException {

    /**
     * Constructor with the payment ID.
     * @param id the ID of the payment that was not found
     */
    public PaymentNotFoundException(Long id) {
        super("Payment not found: " + id);
    }
}
