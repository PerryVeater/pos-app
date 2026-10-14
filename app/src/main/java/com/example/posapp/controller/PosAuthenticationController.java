package com.example.posapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.PosAuthenticateRequest;
import com.example.posapp.dto.PosAuthenticateResponse;
import com.example.posapp.service.PosAuthenticationService;
import com.example.posapp.service.PosClientKeyResolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * POS authentication endpoint.
 * <p>
 * Exchanges a {@code (store, PIN)} pair for a temporary store-scoped bearer
 * session. The controller resolves the client identifier for rate limiting
 * from the request and delegates all credential handling to
 * {@link PosAuthenticationService}; failures are translated centrally by
 * {@link com.example.posapp.exception.ApiExceptionHandler} into generic
 * ProblemDetail responses that reveal nothing about store, employee,
 * assignment, or PIN existence. The raw token appears only in the success
 * body and is never stored or echoed by any other endpoint.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/pos")
public class PosAuthenticationController {

    private final PosAuthenticationService authenticationService;
    private final PosClientKeyResolver clientKeyResolver;

    /**
     * Constructor for PosAuthenticationController.
     * @param authenticationService the authentication orchestrator
     * @param clientKeyResolver the rate-limit client identifier resolver
     */
    public PosAuthenticationController(PosAuthenticationService authenticationService,
                                       PosClientKeyResolver clientKeyResolver) {
        this.authenticationService = authenticationService;
        this.clientKeyResolver = clientKeyResolver;
    }

    /**
     * Authenticate an employee PIN at a store and open a session.
     * @param request the validated store id and PIN payload
     * @param httpRequest the servlet request used to resolve the client key
     * @return the issued session with its one-time raw bearer token
     */
    @PostMapping("/authenticate")
    public ResponseEntity<PosAuthenticateResponse> authenticate(
            @Valid @RequestBody PosAuthenticateRequest request,
            HttpServletRequest httpRequest) {
        String clientKey = clientKeyResolver.resolve(httpRequest);
        PosAuthenticateResponse response =
                authenticationService.authenticate(request.storeId(), request.pin(), clientKey);
        return ResponseEntity.ok(response);
    }
}
