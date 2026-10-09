package com.example.posapp.exception;

/**
 * Thrown when an employee business rule is violated.
 * <p>
 * Raised by the employee service for a blank name or an email that is
 * already used by another employee in the same organization; mapped to
 * HTTP 400 ProblemDetail responses by {@link ApiExceptionHandler}.
 * </p>
 */
public class EmployeeValidationException extends RuntimeException {

    /**
     * Constructor for EmployeeValidationException.
     * @param message the validation failure message
     */
    public EmployeeValidationException(String message) {
        super(message);
    }
}
