package com.example.posapp.exception;

/**
 * Thrown when a POS session cannot be established from a presented bearer
 * token: the {@code Authorization} header is absent or malformed, or the
 * token is unknown, expired, or revoked.
 * <p>
 * Every case collapses to one generic message so a caller cannot tell
 * whether a token once existed, is merely expired, or was never issued.
 * Mapped to HTTP 401 ProblemDetail by {@link ApiExceptionHandler}. The
 * message never echoes the token.
 * </p>
 */
public class PosSessionInvalidException extends RuntimeException {

    /** Generic, non-revealing message shared by every invalid-session path. */
    public static final String GENERIC_MESSAGE =
            "A valid bearer session token is required.";

    /**
     * Constructor using the fixed generic message.
     */
    public PosSessionInvalidException() {
        super(GENERIC_MESSAGE);
    }
}
