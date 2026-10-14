package com.example.posapp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.posapp.dto.PosAuthenticateResponse;
import com.example.posapp.exception.PosAuthFailedException;
import com.example.posapp.exception.PosAuthRateLimitedException;
import com.example.posapp.service.PosAuthenticationService;
import com.example.posapp.service.PosClientKeyResolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Web-layer tests for {@link PosAuthenticationController}: the authenticate
 * route, the success body that carries the one-time token and non-sensitive
 * context, the 400 boundary for structurally invalid payloads, and the
 * generic 401/429 problem mappings. The service and client-key resolver are
 * mocked, so business verification and rate accounting are exercised by the
 * service unit tests and the integration suite instead.
 */
@WebMvcTest(PosAuthenticationController.class)
class PosAuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PosAuthenticationService authenticationService;
    @MockitoBean
    private PosClientKeyResolver clientKeyResolver;

    @Test
    @DisplayName("POST /authenticate returns the issued token and session context")
    void authenticateSuccess() throws Exception {
        when(clientKeyResolver.resolve(any(HttpServletRequest.class))).thenReturn("test-client");
        when(authenticationService.authenticate(123L, "0421", "test-client"))
                .thenReturn(new PosAuthenticateResponse("raw-token-xyz", 10L, "Ada",
                        123L, "Downtown", Instant.parse("2026-10-14T20:00:00Z")));

        mockMvc.perform(post("/api/v1/pos/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":123,\"pin\":\"0421\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("raw-token-xyz"))
                .andExpect(jsonPath("$.employeeId").value(10))
                .andExpect(jsonPath("$.employeeName").value("Ada"))
                .andExpect(jsonPath("$.storeId").value(123))
                .andExpect(jsonPath("$.storeName").value("Downtown"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-14T20:00:00Z"));
    }

    @Test
    @DisplayName("POST /authenticate preserves the leading-zero PIN when calling the service")
    void authenticatePreservesLeadingZeros() throws Exception {
        when(clientKeyResolver.resolve(any(HttpServletRequest.class))).thenReturn("test-client");
        when(authenticationService.authenticate(anyLong(), anyString(), anyString()))
                .thenReturn(new PosAuthenticateResponse("t", 1L, "Ada", 123L, "Downtown", Instant.now()));

        mockMvc.perform(post("/api/v1/pos/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":123,\"pin\":\"0042\"}"))
                .andExpect(status().isOk());

        verify(authenticationService).authenticate(123L, "0042", "test-client");
    }

    @Test
    @DisplayName("POST /authenticate returns 400 for a blank PIN before the service runs")
    void blankPinRejected() throws Exception {
        mockMvc.perform(post("/api/v1/pos/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":123,\"pin\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));

        verify(authenticationService, never()).authenticate(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /authenticate returns 400 when storeId is missing")
    void missingStoreRejected() throws Exception {
        mockMvc.perform(post("/api/v1/pos/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0421\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));

        verify(authenticationService, never()).authenticate(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("POST /authenticate returns a generic 401 that does not echo the PIN")
    void authFailureIsGeneric() throws Exception {
        when(clientKeyResolver.resolve(any(HttpServletRequest.class))).thenReturn("test-client");
        when(authenticationService.authenticate(anyLong(), anyString(), anyString()))
                .thenThrow(new PosAuthFailedException());

        mockMvc.perform(post("/api/v1/pos/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":123,\"pin\":\"0421\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Authentication failed"))
                .andExpect(jsonPath("$.detail").value("Invalid store or PIN."))
                .andExpect(content().string(not(containsString("0421"))));
    }

    @Test
    @DisplayName("POST /authenticate returns 429 when the client is rate limited")
    void rateLimited() throws Exception {
        when(clientKeyResolver.resolve(any(HttpServletRequest.class))).thenReturn("test-client");
        when(authenticationService.authenticate(anyLong(), anyString(), anyString()))
                .thenThrow(new PosAuthRateLimitedException());

        mockMvc.perform(post("/api/v1/pos/authenticate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storeId\":123,\"pin\":\"0421\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.title").value("Too many requests"));
    }
}
