package com.example.posapp.exception;

/**
 * Thrown when a requested modifier group is not found.
 * <p>
 * Used by the modifier group service and controller to signal that a
 * group with the given ID does not exist.
 * </p>
 */
public class ModifierGroupNotFoundException extends RuntimeException {

    /**
     * Constructor with the modifier group ID.
     * @param id the ID of the modifier group that was not found
     */
    public ModifierGroupNotFoundException(Long id) {
        super("Modifier group not found: " + id);
    }
}
