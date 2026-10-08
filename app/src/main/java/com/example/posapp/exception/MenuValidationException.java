package com.example.posapp.exception;

/**
 * Thrown when a menu or menu group business rule is violated.
 * <p>
 * Signals client errors such as a blank name, a duplicate name, an
 * assignment referencing a missing menu or group, or an invalid display
 * order. Mapped to HTTP 400 by the global exception handler.
 * </p>
 */
public class MenuValidationException extends RuntimeException {

    /**
     * Constructor with a detail message.
     * @param message the detail message
     */
    public MenuValidationException(String message) {
        super(message);
    }
}
