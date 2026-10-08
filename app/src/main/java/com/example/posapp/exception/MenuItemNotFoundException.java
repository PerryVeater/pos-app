package com.example.posapp.exception;

/**
 * Thrown when a {@link com.example.posapp.entity.MenuItem} referenced by an
 * operation does not exist.
 * <p>
 * Mapped to HTTP 404 by {@link ApiExceptionHandler}.
 * </p>
 */
public class MenuItemNotFoundException extends RuntimeException {

    /**
     * Constructor for MenuItemNotFoundException.
     * @param id the ID of the menu item that was not found
     */
    public MenuItemNotFoundException(Long id) {
        super("Menu item not found: " + id);
    }
}
