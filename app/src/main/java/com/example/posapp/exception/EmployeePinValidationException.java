package com.example.posapp.exception;

/**
 * Thrown when an employee POS PIN business rule is violated.
 * <p>
 * Signals client errors such as a malformed PIN (wrong length or non-digit
 * characters), setting a PIN for an inactive employee, or a store-scoped
 * uniqueness conflict (another employee sharing a store already claims the
 * PIN). Messages must stay free of secret material - no PIN, no hash, no
 * fingerprint. Mapped to HTTP 400 ProblemDetail responses by
 * {@link ApiExceptionHandler}.
 * </p>
 * <p>
 * Follows the project convention of one shared validation exception per
 * bounded domain (mirrors {@link EmployeeStoreValidationException} for the
 * assignment layer).
 * </p>
 */
public class EmployeePinValidationException extends RuntimeException {

    /**
     * Constructor with a detail message.
     * @param message the detail message
     */
    public EmployeePinValidationException(String message) {
        super(message);
    }
}
