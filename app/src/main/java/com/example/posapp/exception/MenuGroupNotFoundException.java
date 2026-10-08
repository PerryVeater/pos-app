package com.example.posapp.exception;

/**
 * Thrown when a requested menu group is not found.
 * <p>
 * Used by the menu group service and controller to signal that a menu group
 * with the given ID does not exist.
 * </p>
 */
public class MenuGroupNotFoundException extends RuntimeException {

    /**
     * Constructor with the menu group ID.
     * @param id the ID of the menu group that was not found
     */
    public MenuGroupNotFoundException(Long id) {
        super("Menu group not found: " + id);
    }
}
