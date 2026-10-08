package com.example.posapp.exception;

/**
 * Thrown when a requested modifier is not found.
 * <p>
 * Used by the modifier service and controller to signal that a modifier
 * with the given ID does not exist. Also signals a missing
 * (group, modifier) assignment when unassigning.
 * </p>
 */
public class ModifierNotFoundException extends RuntimeException {

    /**
     * Constructor with the modifier ID.
     * @param id the ID of the modifier that was not found
     */
    public ModifierNotFoundException(Long id) {
        super("Modifier not found: " + id);
    }
}
