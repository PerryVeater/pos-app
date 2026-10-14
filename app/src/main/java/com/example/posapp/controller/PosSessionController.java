package com.example.posapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.PosSessionResponse;
import com.example.posapp.service.PosSessionService;

/**
 * POS session endpoints guarded by the bearer token issued at authentication.
 * <p>
 * Both endpoints require an {@code Authorization: Bearer <token>} header.
 * A missing or malformed header, or a token that is unknown, expired, or
 * revoked, is refused with a generic 401 through
 * {@link com.example.posapp.exception.ApiExceptionHandler}; the endpoints
 * never accept a PIN and never return the token or any credential material.
 * These checks apply only here - existing employee, store, menu, order, and
 * management endpoints remain unauthenticated as before.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/pos")
public class PosSessionController {

    private final PosSessionService sessionService;

    /**
     * Constructor for PosSessionController.
     * @param sessionService the session lifecycle service
     */
    public PosSessionController(PosSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Return the non-sensitive employee/store context of the current session.
     * @param authorization the raw {@code Authorization} header, possibly absent
     * @return the current session view
     */
    @GetMapping("/session")
    public PosSessionResponse session(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return sessionService.currentSession(authorization);
    }

    /**
     * Revoke the current session. Idempotent: repeated logouts and logouts of
     * already-expired sessions all succeed quietly with no content.
     * @param authorization the raw {@code Authorization} header, possibly absent
     * @return 204 No Content
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        sessionService.logout(authorization);
        return ResponseEntity.noContent().build();
    }
}
