package com.example.posapp.dto;

import java.time.Instant;

import com.example.posapp.entity.PosSession;

/**
 * Response to a successful POS PIN authentication.
 * <p>
 * Carries the freshly minted opaque {@code token} - the raw bearer token is
 * returned exactly here and never again, and only its hash is persisted -
 * alongside just enough non-sensitive context for the client to identify the
 * employee and store the session is scoped to and when it expires. No PIN,
 * hash, fingerprint, or stored token hash appears in this payload.
 * </p>
 *
 * @param token the opaque bearer token (present only in this response)
 * @param employeeId the authenticated employee ID
 * @param employeeName the authenticated employee name
 * @param storeId the store the session was opened at
 * @param storeName the store name
 * @param expiresAt the absolute expiry instant (UTC)
 */
public record PosAuthenticateResponse(
        String token,
        Long employeeId,
        String employeeName,
        Long storeId,
        String storeName,
        Instant expiresAt) {

    /**
     * Build the response from a freshly created session and its raw token.
     * The session's associations must be initialized when called.
     * @param session the persisted session
     * @param rawToken the one-time bearer token to hand back
     * @return the authentication response
     */
    public static PosAuthenticateResponse of(PosSession session, String rawToken) {
        return new PosAuthenticateResponse(
                rawToken,
                session.getEmployee().getId(),
                session.getEmployee().getName(),
                session.getStore().getId(),
                session.getStore().getName(),
                session.getExpiresAt());
    }
}
