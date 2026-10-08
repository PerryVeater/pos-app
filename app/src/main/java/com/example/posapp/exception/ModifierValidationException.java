package com.example.posapp.exception;

/**
 * Thrown when a modifier / modifier group business rule is violated.
 * <p>
 * Signals client errors such as a blank name, a duplicate name, an invalid
 * selection policy ({@code minSelections < 0} or {@code maxSelections <
 * minSelections}), or an attempt to delete a modifier still assigned to a
 * group. Mapped to HTTP 400 by the global exception handler.
 * </p>
 */
public class ModifierValidationException extends RuntimeException {

    /**
     * Constructor with a detail message.
     * @param message the detail message
     */
    public ModifierValidationException(String message) {
        super(message);
    }
}
