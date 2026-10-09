package com.example.posapp.exception;

/**
 * Thrown when an employee cannot be found or referenced.
 * <p>
 * Raised by the employee service when an operation targets an employee ID
 * that does not exist, including get, update, and delete requests; mapped
 * to HTTP 404 ProblemDetail responses by {@link ApiExceptionHandler}.
 * </p>
 */
public class EmployeeNotFoundException extends RuntimeException {

    /**
     * Constructor for EmployeeNotFoundException.
     * @param id the employee ID that was not found
     */
    public EmployeeNotFoundException(Long id) {
        super("Employee not found: " + id);
    }
}
