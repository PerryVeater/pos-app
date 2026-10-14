package com.example.posapp.exception;

/**
 * Thrown when a client exceeds the configured POS PIN-authentication attempt
 * limit for a store within the rate-limit window.
 * <p>
 * The response is deliberately generic and says nothing about whether the
 * supplied credentials were valid - a throttled request is refused before any
 * credential is examined - so rate limiting cannot be used to distinguish
 * good from bad PINs. Mapped to HTTP 429 ProblemDetail by
 * {@link ApiExceptionHandler}.
 * </p>
 */
public class PosAuthRateLimitedException extends RuntimeException {

    /** Generic throttle text that reveals nothing about credential validity. */
    public static final String GENERIC_MESSAGE =
            "Too many authentication attempts. Try again later.";

    /**
     * Constructor using the fixed generic message.
     */
    public PosAuthRateLimitedException() {
        super(GENERIC_MESSAGE);
    }
}
