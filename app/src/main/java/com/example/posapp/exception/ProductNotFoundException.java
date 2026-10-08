package com.example.posapp.exception;

/**
 * Thrown when a {@link com.example.posapp.entity.Product} referenced by an
 * operation does not exist.
 * <p>
 * Mapped to HTTP 404 by {@link ApiExceptionHandler}.
 * </p>
 */
public class ProductNotFoundException extends RuntimeException {

    /**
     * Constructor for ProductNotFoundException.
     * @param id the ID of the product that was not found
     */
    public ProductNotFoundException(Long id) {
        super("Product not found: " + id);
    }
}
