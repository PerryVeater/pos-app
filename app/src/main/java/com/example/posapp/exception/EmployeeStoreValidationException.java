package com.example.posapp.exception;

/**
 * Thrown when an employee-to-store assignment business rule is violated.
 * <p>
 * Signals client errors such as an assignment attempt between an employee
 * and a store that belong to different organizations. Mapped to HTTP 400
 * ProblemDetail responses by {@link ApiExceptionHandler}.
 * </p>
 * <p>
 * Follows the project convention of one shared validation exception per
 * bounded domain (mirrors {@link EmployeeGroupValidationException} for
 * the employee group membership layer).
 * </p>
 */
public class EmployeeStoreValidationException extends RuntimeException {

    /**
     * Constructor with a detail message.
     * @param message the detail message
     */
    public EmployeeStoreValidationException(String message) {
        super(message);
    }
}
