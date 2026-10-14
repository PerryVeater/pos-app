package com.example.posapp.dto;

import java.time.Instant;

import com.example.posapp.entity.PosSession;

/**
 * Non-sensitive view of an active POS session, returned by the session
 * endpoint so a client can identify who and where its bearer token maps to.
 * <p>
 * Deliberately excludes the token itself (the raw token is only ever present
 * in the authentication response), the stored token hash, and any PIN or
 * credential material. Only the authenticated employee and store identity
 * plus the absolute expiry are exposed.
 * </p>
 *
 * @param employeeId the authenticated employee ID
 * @param employeeName the authenticated employee name
 * @param storeId the store the session was opened at
 * @param storeName the store name
 * @param expiresAt the absolute expiry instant (UTC)
 */
public record PosSessionResponse(
        Long employeeId,
        String employeeName,
        Long storeId,
        String storeName,
        Instant expiresAt) {

    /**
     * Map a {@link PosSession} to its non-sensitive API representation.
     * The session's employee and store associations must be initialized when
     * this is called (it is built inside the service's read transaction).
     * @param session the session entity
     * @return the API representation
     */
    public static PosSessionResponse from(PosSession session) {
        return new PosSessionResponse(
                session.getEmployee().getId(),
                session.getEmployee().getName(),
                session.getStore().getId(),
                session.getStore().getName(),
                session.getExpiresAt());
    }
}
