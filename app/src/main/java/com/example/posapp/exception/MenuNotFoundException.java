package com.example.posapp.exception;

/**
 * Thrown when a requested menu is not found.
 * <p>
 * Used by the menu service and controller to signal that a menu with the
 * given ID does not exist.
 * </p>
 */
public class MenuNotFoundException extends RuntimeException {

    /**
     * Constructor with the menu ID.
     * @param id the ID of the menu that was not found
     */
    public MenuNotFoundException(Long id) {
        super("Menu not found: " + id);
    }
}
