package com.example.posapp.exception;

/**
 * Thrown when an {@link com.example.posapp.entity.Order} referenced by an
 * operation does not exist.
 * <p>
 * Mapped to HTTP 404 by {@link ApiExceptionHandler}.
 * </p>
 */
public class OrderNotFoundException extends RuntimeException {

    /**
     * Constructor for OrderNotFoundException.
     * @param id the ID of the order that was not found
     */
    public OrderNotFoundException(Long id) {
        super("Order not found: " + id);
    }
}
