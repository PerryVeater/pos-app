package com.example.posapp.exception;

/**
 * Thrown when a requested store is not found.
 * <p>
 * Used by the store service and controller to signal that a store with the
 * given ID does not exist.
 * </p>
 */
public class StoreNotFoundException extends RuntimeException {

    /**
     * Constructor with the store ID.
     * @param id the ID of the store that was not found
     */
    public StoreNotFoundException(Long id) {
        super("Store not found: " + id);
    }
}
