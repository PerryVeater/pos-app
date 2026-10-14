package com.example.posapp.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.posapp.dto.PosAuthenticateResponse;
import com.example.posapp.dto.PosSessionResponse;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.PosSession;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.PosSessionInvalidException;
import com.example.posapp.repository.PosSessionRepository;

/**
 * Owns the POS session lifecycle: issuing a session when authentication
 * succeeds, resolving a presented bearer token to the current session, and
 * revoking a session on logout.
 * <p>
 * Issuance is atomic and credential-free: a fresh CSPRNG token is generated,
 * only its SHA-256 hash is persisted, and the raw token is returned once in
 * the response. Resolution re-derives the hash from whatever the client
 * presents and looks the session up by it, then rejects anything unknown,
 * expired, or revoked through the single generic
 * {@link PosSessionInvalidException} so no probe can distinguish those
 * states. Logout stamps {@code revokedAt} and is idempotent - a token that is
 * already revoked or was never issued is simply a no-op, so repeated logout
 * is safe and predictable.
 * </p>
 * <p>
 * The {@link #startSession(Employee, Store)} and {@link #logout(String)}
 * methods run in a read-write transaction; {@link #currentSession(String)}
 * runs in a read-only one and materializes the employee/store names into the
 * response DTO inside the transaction so no lazy proxy escapes it.
 * </p>
 */
@Service
public class PosSessionService {

    /** Authorization scheme prefix for bearer tokens (RFC 6750). */
    private static final String BEARER_PREFIX = "Bearer ";

    private final SessionProperties sessionProperties;
    private final PosSessionTokenService tokenService;
    private final PosSessionRepository sessionRepository;

    /**
     * Constructor for PosSessionService.
     * @param sessionProperties the configured session lifetime
     * @param tokenService the bearer-token generator/hasher
     * @param sessionRepository the session repository
     */
    public PosSessionService(SessionProperties sessionProperties,
                             PosSessionTokenService tokenService,
                             PosSessionRepository sessionRepository) {
        this.sessionProperties = sessionProperties;
        this.tokenService = tokenService;
        this.sessionRepository = sessionRepository;
    }

    /**
     * Issue a store-scoped session for an authenticated employee, returning
     * the one-time raw token plus non-sensitive session context.
     * @param employee the authenticated employee
     * @param store the store the session is scoped to
     * @return the authentication response carrying the raw bearer token
     */
    @Transactional
    public PosAuthenticateResponse startSession(Employee employee, Store store) {
        String rawToken = tokenService.generateToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(sessionProperties.ttl());
        PosSession session = sessionRepository.saveAndFlush(
                new PosSession(employee, store, tokenService.hash(rawToken), now, expiresAt));
        return PosAuthenticateResponse.of(session, rawToken);
    }

    /**
     * Resolve a presented {@code Authorization} header to the current live
     * session.
     * @param authorizationHeader the raw HTTP {@code Authorization} header
     * @return the non-sensitive session view
     * @throws PosSessionInvalidException when the header is missing or
     *         malformed, or the token is unknown, expired, or revoked
     */
    @Transactional(readOnly = true)
    public PosSessionResponse currentSession(String authorizationHeader) {
        String token = requireBearerToken(authorizationHeader);
        PosSession session = sessionRepository.findByTokenHash(tokenService.hash(token))
                .orElseThrow(PosSessionInvalidException::new);
        if (!session.isLive(Instant.now())) {
            throw new PosSessionInvalidException();
        }
        return PosSessionResponse.from(session);
    }

    /**
     * Revoke the session named by the {@code Authorization} header. Idempotent
     * and safe to repeat: an already-revoked or unknown token is a no-op, so
     * the caller observes the same outcome however many times it logs out.
     * @param authorizationHeader the raw HTTP {@code Authorization} header
     * @throws PosSessionInvalidException when the header is missing or
     *         malformed (a request with no bearer token to log out is refused)
     */
    @Transactional
    public void logout(String authorizationHeader) {
        String token = requireBearerToken(authorizationHeader);
        sessionRepository.findByTokenHash(tokenService.hash(token))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> {
                    session.setRevokedAt(Instant.now());
                    sessionRepository.save(session);
                });
    }

    /**
     * Extract the bearer token from an {@code Authorization} header. The
     * scheme match is case-insensitive; anything else - a null header, a
     * non-bearer scheme, or an empty token - is rejected as an invalid
     * session rather than surfaced as a distinct error.
     * @param authorizationHeader the raw header value, possibly {@code null}
     * @return the bearer token substring
     * @throws PosSessionInvalidException when no bearer token is present
     */
    private static String requireBearerToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            throw new PosSessionInvalidException();
        }
        String trimmed = authorizationHeader.trim();
        if (!trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new PosSessionInvalidException();
        }
        String token = trimmed.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new PosSessionInvalidException();
        }
        return token;
    }
}
