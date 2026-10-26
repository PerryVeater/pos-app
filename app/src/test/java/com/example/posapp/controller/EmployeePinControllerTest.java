package com.example.posapp.controller;

import static com.example.posapp.controller.BrandScopeTestSupport.BRAND_ID;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeePinValidationException;
import com.example.posapp.service.EmployeePinService;

/**
 * Web-layer tests for {@link EmployeePinController} using MockMvc.
 * <p>
 * The service is mocked, so these tests assert the HTTP contract only: the
 * set/remove routes, the 204 empty responses that must never carry a body
 * (no hash, fingerprint, or PIN echo can leak), and the RFC 9457
 * problem-detail mapping for missing employees (404), malformed PINs and
 * store-scoped conflicts (400), and a blank request body (400 via bean
 * validation). Validation of PIN format against the configured window and
 * the uniqueness enforcement are covered by the service unit tests and the
 * Testcontainers integration tests.
 * </p>
 * <p>
 * Both routes are confined to the caller's brand, so these tests also assert
 * that the brand from the resolved back-office identity reaches the service.
 * </p>
 */
@WebMvcTest(EmployeePinController.class)
@AutoConfigureMockMvc(addFilters = false)
class EmployeePinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeePinService pinService;

    @BeforeEach
    void signIn() {
        BrandScopeTestSupport.signIn();
    }

    @AfterEach
    void signOut() {
        BrandScopeTestSupport.signOut();
    }

    // --- PUT /api/v1/employees/{employeeId}/pin ---

    @Test
    @DisplayName("PUT /pin returns 204 with an empty body on success")
    void setPinReturns204NoBody() throws Exception {
        mockMvc.perform(put("/api/v1/employees/1/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0421\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(pinService).setPin(1L, "0421", BRAND_ID);
    }

    @Test
    @DisplayName("PUT /pin preserves leading zeros in the deserialized PIN")
    void setPinPreservesLeadingZeros() throws Exception {
        mockMvc.perform(put("/api/v1/employees/1/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"0012\"}"))
                .andExpect(status().isNoContent());

        verify(pinService).setPin(1L, "0012", BRAND_ID);
    }

    @Test
    @DisplayName("PUT /pin returns 400 for a blank PIN before the service runs")
    void setPinBlankBodyReturns400() throws Exception {
        mockMvc.perform(put("/api/v1/employees/1/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));

        verify(pinService, never()).setPin(anyLong(), anyString(), anyLong());
    }

    @Test
    @DisplayName("PUT /pin returns 404 when the employee is missing")
    void setPinMissingEmployeeReturns404() throws Exception {
        doThrow(new EmployeeNotFoundException(99L))
                .when(pinService).setPin(99L, "1234", BRAND_ID);

        mockMvc.perform(put("/api/v1/employees/99/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Employee not found"))
                .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    @DisplayName("PUT /pin returns 400 for a malformed PIN")
    void setPinMalformedReturns400() throws Exception {
        doThrow(new EmployeePinValidationException("PIN must be 4 to 6 digits"))
                .when(pinService).setPin(1L, "12", BRAND_ID);

        mockMvc.perform(put("/api/v1/employees/1/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid employee PIN"))
                .andExpect(jsonPath("$.detail").value(containsString("digits")));
    }

    @Test
    @DisplayName("PUT /pin returns 400 for a store-scoped conflict, with no secret in the body")
    void setPinConflictReturns400() throws Exception {
        doThrow(new EmployeePinValidationException(
                "PIN is already in use by another employee assigned to the same store: employeeId=1"))
                .when(pinService).setPin(1L, "1234", BRAND_ID);

        mockMvc.perform(put("/api/v1/employees/1/pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid employee PIN"))
                .andExpect(jsonPath("$.detail").value(containsString("already in use")))
                // The response must not echo the submitted PIN.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        containsString("1234"))));
    }

    // --- DELETE /api/v1/employees/{employeeId}/pin ---

    @Test
    @DisplayName("DELETE /pin returns 204 on success")
    void removePinReturns204() throws Exception {
        mockMvc.perform(delete("/api/v1/employees/1/pin"))
                .andExpect(status().isNoContent());

        verify(pinService).removePin(1L, BRAND_ID);
    }

    @Test
    @DisplayName("DELETE /pin returns 204 when the employee has no PIN (idempotent no-op)")
    void removePinMissingCredentialIsIdempotent() throws Exception {
        // The service's contract is that removing an absent PIN is a silent
        // no-op, so nothing is mocked to throw and the response is still 204.
        mockMvc.perform(delete("/api/v1/employees/1/pin"))
                .andExpect(status().isNoContent());

        verify(pinService).removePin(1L, BRAND_ID);
    }

    @Test
    @DisplayName("DELETE /pin returns 404 when the employee is missing")
    void removePinMissingEmployeeReturns404() throws Exception {
        doThrow(new EmployeeNotFoundException(99L))
                .when(pinService).removePin(99L, BRAND_ID);

        mockMvc.perform(delete("/api/v1/employees/99/pin"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Employee not found"));
    }

    @Test
    @DisplayName("DELETE /pin rejects a non-numeric employee id with 400")
    void removePinNonNumericIdReturns400() throws Exception {
        mockMvc.perform(delete("/api/v1/employees/abc/pin"))
                .andExpect(status().isBadRequest());

        verify(pinService, never()).removePin(anyLong(), anyLong());
    }
}
