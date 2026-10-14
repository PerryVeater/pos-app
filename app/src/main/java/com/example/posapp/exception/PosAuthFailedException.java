package com.example.posapp.exception;

/**
 * Thrown when POS PIN authentication fails for any reason: unknown store,
 * inactive store, no employee with a matching PIN assigned to the store, a
 * wrong PIN, or an inactive employee.
 * <p>
 * The failure is intentionally indistinguishable across all of those cases -
 * a single fixed message is used - so a caller cannot probe which stores
 * exist, which employees are assigned, who holds a credential, or whether a
 * PIN was merely wrong. That consistency is what keeps the endpoint from
 * leaking account enumeration signals. Mapped to a generic HTTP 401
 * ProblemDetail by {@link ApiExceptionHandler}. The message never contains
 * the PIN, store id, or any credential material.
 * </p>
 */
public class PosAuthFailedException extends RuntimeException {

    /** Generic, non-revealing failure text shared by all auth-failure paths. */
    public static final String GENERIC_MESSAGE = "Invalid store or PIN.";

    /**
     * Constructor using the fixed generic message.
     */
    public PosAuthFailedException() {
        super(GENERIC_MESSAGE);
    }
}
