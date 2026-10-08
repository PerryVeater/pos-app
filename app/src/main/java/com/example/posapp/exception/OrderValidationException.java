package com.example.posapp.exception;

/**
 * Thrown when an order creation or business-rule validation fails.
 * <p>
 * Covers cases such as empty line lists, non-positive quantities,
 * nonexistent products, and inactive products. Mapped to HTTP 400 by
 * {@link ApiExceptionHandler}.
 * </p>
 */
public class OrderValidationException extends RuntimeException {

    /**
     * Constructor for OrderValidationException.
     * @param message a description of the validation failure
     */
    public OrderValidationException(String message) {
        super(message);
    }
}
