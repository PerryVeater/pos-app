package com.example.posapp.exception;

/**
 * Thrown when an employee group business rule is violated.
 * <p>
 * Signals client errors such as a blank name, a parent group that belongs
 * to a different organization, an attempt to make a group its own parent,
 * an attempt to introduce a circular hierarchy, or an attempt to delete a
 * group that still has child groups. Mapped to HTTP 400 by the global
 * exception handler.
 * </p>
 * <p>
 * Follows the project convention of one shared validation exception per
 * bounded domain (mirrors {@link OrganizationValidationException}
 * covering both Organization and Store).
 * </p>
 */
public class EmployeeGroupValidationException extends RuntimeException {

    /**
     * Constructor with a detail message.
     * @param message the detail message
     */
    public EmployeeGroupValidationException(String message) {
        super(message);
    }
}
