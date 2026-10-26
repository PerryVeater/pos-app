package com.example.posapp.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.posapp.dto.PosSessionResponse;
import com.example.posapp.exception.PosSessionInvalidException;
import com.example.posapp.service.PosSessionService;

/**
 * Web-layer tests for {@link PosSessionController}: the bearer-guarded session
 * read, the idempotent logout, and the generic 401 mapping when the
 * {@code Authorization} header is missing, malformed, or refers to a dead
 * session. The service is mocked; token validation itself is covered by the
 * session-service unit tests and the integration suite.
 */
@WebMvcTest(PosSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class PosSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PosSessionService sessionService;

    @Test
    @DisplayName("GET /session returns the current session for a bearer token")
    void sessionReturnsContext() throws Exception {
        when(sessionService.currentSession("Bearer tok"))
                .thenReturn(new PosSessionResponse(10L, "Ada", 123L, "Downtown",
                        Instant.parse("2026-10-14T20:00:00Z")));

        mockMvc.perform(get("/api/v1/pos/session").header("Authorization", "Bearer tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(10))
                .andExpect(jsonPath("$.employeeName").value("Ada"))
                .andExpect(jsonPath("$.storeId").value(123))
                // The response never carries a token field.
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    @DisplayName("GET /session without a bearer header returns 401")
    void sessionWithoutHeaderUnauthorized() throws Exception {
        when(sessionService.currentSession(null)).thenThrow(new PosSessionInvalidException());

        mockMvc.perform(get("/api/v1/pos/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }

    @Test
    @DisplayName("GET /session with a dead or malformed token returns 401")
    void sessionWithBadTokenUnauthorized() throws Exception {
        when(sessionService.currentSession(anyString())).thenThrow(new PosSessionInvalidException());

        mockMvc.perform(get("/api/v1/pos/session").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }

    @Test
    @DisplayName("POST /logout revokes the session and returns 204")
    void logoutRevokesAndReturns204() throws Exception {
        mockMvc.perform(post("/api/v1/pos/logout").header("Authorization", "Bearer tok"))
                .andExpect(status().isNoContent());

        verify(sessionService).logout("Bearer tok");
    }

    @Test
    @DisplayName("repeated POST /logout stays 204 (idempotent)")
    void logoutIsRepeatable() throws Exception {
        mockMvc.perform(post("/api/v1/pos/logout").header("Authorization", "Bearer tok"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/pos/logout").header("Authorization", "Bearer tok"))
                .andExpect(status().isNoContent());

        verify(sessionService, org.mockito.Mockito.times(2)).logout("Bearer tok");
    }

    @Test
    @DisplayName("POST /logout without a bearer header returns 401")
    void logoutWithoutHeaderUnauthorized() throws Exception {
        doThrow(new PosSessionInvalidException()).when(sessionService).logout(null);

        mockMvc.perform(post("/api/v1/pos/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }
}
