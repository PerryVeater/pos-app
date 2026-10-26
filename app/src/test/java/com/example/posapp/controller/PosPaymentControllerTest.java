package com.example.posapp.controller;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.posapp.dto.PaymentRequest;
import com.example.posapp.dto.PaymentStatusUpdateRequest;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.exception.OrderNotFoundException;
import com.example.posapp.exception.PaymentNotFoundException;
import com.example.posapp.exception.PaymentValidationException;
import com.example.posapp.exception.PosSessionInvalidException;
import com.example.posapp.service.PosPaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Web-layer tests for {@link PosPaymentController}: the bearer-guarded create,
 * retrieve, and status-update mappings, the generic 401 for a missing or dead
 * session, the identical 404 for a missing or cross-store payment/order, and the
 * 400 validation responses. The service is mocked; the ownership and session
 * rules themselves are covered by the service unit and integration tests.
 */
@WebMvcTest(PosPaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PosPaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PosPaymentService posPaymentService;

    /** A payment for order 1, mirroring what the POS path persists. */
    private static Payment samplePayment(PaymentStatus status) {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(1L);
        Payment payment = new Payment(order, new BigDecimal("25.00"), PaymentMethod.CARD,
                status, Instant.parse("2026-10-14T12:00:00Z"));
        payment.setId(1L);
        return payment;
    }

    private static PaymentRequest sampleRequest() {
        return new PaymentRequest(1L, new BigDecimal("25.00"), PaymentMethod.CARD);
    }

    // --- POST /api/v1/pos/payments ---

    @Test
    @DisplayName("POST /api/v1/pos/payments: valid session returns 201 with the payment mapping")
    void createWithSessionReturns201() throws Exception {
        Payment created = samplePayment(PaymentStatus.PENDING);
        when(posPaymentService.createPayment(eq("Bearer tok"), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/pos/payments")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-14T12:00:00Z"));
    }

    @Test
    @DisplayName("POST /api/v1/pos/payments: missing Authorization header returns 401, not 400")
    void createWithoutHeaderReturns401() throws Exception {
        when(posPaymentService.createPayment(any(), any()))
                .thenThrow(new PosSessionInvalidException());

        mockMvc.perform(post("/api/v1/pos/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }

    @Test
    @DisplayName("POST /api/v1/pos/payments: expired or revoked token returns 401")
    void createWithDeadTokenReturns401() throws Exception {
        when(posPaymentService.createPayment(anyString(), any()))
                .thenThrow(new PosSessionInvalidException());

        mockMvc.perform(post("/api/v1/pos/payments")
                        .header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }

    @Test
    @DisplayName("POST /api/v1/pos/payments: invalid amount returns 400")
    void createInvalidAmountReturns400() throws Exception {
        when(posPaymentService.createPayment(eq("Bearer tok"), any()))
                .thenThrow(new PaymentValidationException("Payment amount must be greater than zero"));

        mockMvc.perform(post("/api/v1/pos/payments")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid payment"));
    }

    @Test
    @DisplayName("POST /api/v1/pos/payments: missing or foreign order returns the generic 404")
    void createForeignOrderReturnsGeneric404() throws Exception {
        when(posPaymentService.createPayment(eq("Bearer tok"), any()))
                .thenThrow(new OrderNotFoundException(1L));

        mockMvc.perform(post("/api/v1/pos/payments")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order not found"))
                .andExpect(jsonPath("$.detail").value("Order not found: 1"));
    }

    // --- GET /api/v1/pos/payments/{id} ---

    @Test
    @DisplayName("GET /api/v1/pos/payments/{id}: same-store payment returns 200")
    void getSameStoreReturns200() throws Exception {
        Payment fetched = samplePayment(PaymentStatus.COMPLETED);
        when(posPaymentService.getPayment("Bearer tok", 5L))
                .thenReturn(fetched);

        mockMvc.perform(get("/api/v1/pos/payments/5").header("Authorization", "Bearer tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/v1/pos/payments/{id}: missing and cross-store payments return the same 404")
    void getForeignPaymentReturnsGeneric404() throws Exception {
        when(posPaymentService.getPayment(eq("Bearer tok"), anyLong()))
                .thenThrow(new PaymentNotFoundException(5L));

        mockMvc.perform(get("/api/v1/pos/payments/5").header("Authorization", "Bearer tok"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment not found"))
                .andExpect(jsonPath("$.detail").value("Payment not found: 5"));
    }

    @Test
    @DisplayName("GET /api/v1/pos/payments/{id}: invalid session returns 401")
    void getWithoutSessionReturns401() throws Exception {
        when(posPaymentService.getPayment(any(), anyLong()))
                .thenThrow(new PosSessionInvalidException());

        mockMvc.perform(get("/api/v1/pos/payments/5"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }

    // --- PATCH /api/v1/pos/payments/{id}/status ---

    @Test
    @DisplayName("PATCH /api/v1/pos/payments/{id}/status: valid transition returns 200")
    void patchStatusValidReturns200() throws Exception {
        Payment updated = samplePayment(PaymentStatus.COMPLETED);
        when(posPaymentService.updateStatus("Bearer tok", 5L, PaymentStatus.COMPLETED))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/pos/payments/5/status")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentStatusUpdateRequest(PaymentStatus.COMPLETED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/pos/payments/{id}/status: invalid transition returns 400")
    void patchStatusInvalidTransitionReturns400() throws Exception {
        when(posPaymentService.updateStatus(eq("Bearer tok"), anyLong(), any()))
                .thenThrow(new PaymentValidationException("Cannot transition from PENDING to REFUNDED"));

        mockMvc.perform(patch("/api/v1/pos/payments/5/status")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentStatusUpdateRequest(PaymentStatus.REFUNDED))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid payment"));
    }

    @Test
    @DisplayName("PATCH /api/v1/pos/payments/{id}/status: missing or cross-store payment returns 404")
    void patchStatusForeignPaymentReturns404() throws Exception {
        when(posPaymentService.updateStatus(eq("Bearer tok"), anyLong(), any()))
                .thenThrow(new PaymentNotFoundException(5L));

        mockMvc.perform(patch("/api/v1/pos/payments/5/status")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentStatusUpdateRequest(PaymentStatus.COMPLETED))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment not found"));
    }

    @Test
    @DisplayName("PATCH /api/v1/pos/payments/{id}/status: a lost update returns 409, not 500")
    void patchStatusConcurrentUpdateReturns409() throws Exception {
        when(posPaymentService.updateStatus(eq("Bearer tok"), anyLong(), any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(Payment.class, 5L));

        mockMvc.perform(patch("/api/v1/pos/payments/5/status")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentStatusUpdateRequest(PaymentStatus.COMPLETED))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflicting update"));
    }

    @Test
    @DisplayName("PATCH /api/v1/pos/payments/{id}/status: invalid session returns 401")
    void patchStatusWithoutSessionReturns401() throws Exception {
        when(posPaymentService.updateStatus(any(), anyLong(), any()))
                .thenThrow(new PosSessionInvalidException());

        mockMvc.perform(patch("/api/v1/pos/payments/5/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new PaymentStatusUpdateRequest(PaymentStatus.COMPLETED))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Invalid session"));
    }

    @Test
    @DisplayName("PATCH /api/v1/pos/payments/{id}/status: an invalid status value returns 400")
    void patchInvalidStatusValueReturns400() throws Exception {
        mockMvc.perform(patch("/api/v1/pos/payments/5/status")
                        .header("Authorization", "Bearer tok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVALID_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request body"));
    }
}
