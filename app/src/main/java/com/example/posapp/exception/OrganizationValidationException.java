package com.example.posapp.exception;

/**
 * Thrown when an organization or store business rule is violated.
 * <p>
 * Signals client errors such as a blank name, a duplicate name across
 * organizations or stores, an attempt to delete an organization that still
 * owns stores, or an attempt to attach a store to a missing organization.
 * Mapped to HTTP 400 by the global exception handler.
 * </p>
 * <p>
 * Follows the project convention of one shared validation exception per
 * bounded domain (mirrors {@link MenuValidationException} covering both
 * Menu and MenuGroup and {@link ModifierValidationException} covering both
 * ModifierGroup and Modifier).
 * </p>
 */
public class OrganizationValidationException extends RuntimeException {

    /**
     * Constructor with a detail message.
     * @param message the detail message
     */
    public OrganizationValidationException(String message) {
        super(message);
    }
}
