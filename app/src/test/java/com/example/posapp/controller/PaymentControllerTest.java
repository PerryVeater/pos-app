package com.example.posapp.controller;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.posapp.dto.PaymentRequest;
import com.example.posapp.dto.PaymentStatusUpdateRequest;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.exception.PaymentNotFoundException;
import com.example.posapp.exception.PaymentValidationException;
import com.example.posapp.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link WebMvcTest} tests for {@link PaymentController}.
 * <p>
 * Uses MockMvc to test HTTP request/response handling in isolation from the
 * real service layer. The {@link PaymentService} is mocked.
 * </p>
 */
@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PaymentService paymentService;

    // --- POST /api/v1/payments ---

    @Test
    @DisplayName("POST /api/v1/payments: valid payment returns 201 with response mapping")
    void postValidPaymentReturns201() throws Exception {
        Order order = new Order(OrderStatus.CONFIRMED, LocalDateTime.of(2026, 10, 7, 12, 0));
        setField(order, "id", 1L);
        Payment payment = new Payment(
                order, new BigDecimal("25.00"), PaymentMethod.CARD,
                PaymentStatus.PENDING, LocalDateTime.of(2026, 10, 7, 12, 0));
        payment.setId(1L);

        when(paymentService.createPayment(eq(1L), any(BigDecimal.class), eq(PaymentMethod.CARD)))
                .thenReturn(payment);

        PaymentRequest request = new PaymentRequest(1L, new BigDecimal("25.00"), PaymentMethod.CARD);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.method").value("CARD"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.createdAt").exists());

        verify(paymentService).createPayment(eq(1L), any(BigDecimal.class), eq(PaymentMethod.CARD));
    }

    @Test
    @DisplayName("POST /api/v1/payments: invalid amount returns 400")
    void postInvalidAmountReturns400() throws Exception {
        when(paymentService.createPayment(eq(1L), any(BigDecimal.class), eq(PaymentMethod.CARD)))
                .thenThrow(new PaymentValidationException("Payment amount must be greater than zero"));

        PaymentRequest request = new PaymentRequest(1L, BigDecimal.ZERO, PaymentMethod.CARD);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid payment"))
                .andExpect(jsonPath("$.detail").value("Payment amount must be greater than zero"));
    }

    @Test
    @DisplayName("POST /api/v1/payments: missing order returns 400")
    void postMissingOrderReturns400() throws Exception {
        when(paymentService.createPayment(eq(99L), any(BigDecimal.class), eq(PaymentMethod.CARD)))
                .thenThrow(new PaymentValidationException("Order not found: 99"));

        PaymentRequest request = new PaymentRequest(99L, new BigDecimal("10.00"), PaymentMethod.CARD);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid payment"))
                .andExpect(jsonPath("$.detail").value("Order not found: 99"));
    }

    @Test
    @DisplayName("POST /api/v1/payments: cancelled order returns 400")
    void postCancelledOrderReturns400() throws Exception {
        when(paymentService.createPayment(eq(1L), any(BigDecimal.class), eq(PaymentMethod.CARD)))
                .thenThrow(new PaymentValidationException("Cannot create payment for a cancelled order: 1"));

        PaymentRequest request = new PaymentRequest(1L, new BigDecimal("10.00"), PaymentMethod.CARD);

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid payment"))
                .andExpect(jsonPath("$.detail").value("Cannot create payment for a cancelled order: 1"));
    }

    // --- GET /api/v1/payments/{id} ---

    @Test
    @DisplayName("GET /api/v1/payments/{id}: existing payment returns 200 with response mapping")
    void getExistingPaymentReturns200() throws Exception {
        Order order = new Order(OrderStatus.CONFIRMED, LocalDateTime.of(2026, 10, 7, 12, 0));
        setField(order, "id", 1L);
        Payment payment = new Payment(
                order, new BigDecimal("50.00"), PaymentMethod.CASH,
                PaymentStatus.COMPLETED, LocalDateTime.of(2026, 10, 7, 12, 0));
        payment.setId(1L);

        when(paymentService.getPaymentById(1L)).thenReturn(java.util.Optional.of(payment));

        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderId").value(1))
                .andExpect(jsonPath("$.amount").value(50.00))
                .andExpect(jsonPath("$.method").value("CASH"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id}: missing payment returns 404")
    void getMissingPaymentReturns404() throws Exception {
        when(paymentService.getPaymentById(99L)).thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/v1/payments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment not found"))
                .andExpect(jsonPath("$.detail").value("Payment not found: 99"));
    }

    // --- PATCH /api/v1/payments/{id}/status ---

    @Test
    @DisplayName("PATCH /api/v1/payments/{id}/status: valid transition returns 200 with updated payment")
    void patchValidTransitionReturns200() throws Exception {
        Order order = new Order(OrderStatus.CONFIRMED, LocalDateTime.of(2026, 10, 7, 12, 0));
        setField(order, "id", 1L);
        Payment payment = new Payment(
                order, new BigDecimal("25.00"), PaymentMethod.CARD,
                PaymentStatus.COMPLETED, LocalDateTime.of(2026, 10, 7, 12, 0));
        payment.setId(1L);

        when(paymentService.transitionStatus(1L, PaymentStatus.COMPLETED)).thenReturn(payment);

        PaymentStatusUpdateRequest request = new PaymentStatusUpdateRequest(PaymentStatus.COMPLETED);

        mockMvc.perform(patch("/api/v1/payments/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderId").value(1));

        verify(paymentService).transitionStatus(1L, PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("PATCH /api/v1/payments/{id}/status: invalid transition returns 400")
    void patchInvalidTransitionReturns400() throws Exception {
        when(paymentService.transitionStatus(1L, PaymentStatus.REFUNDED))
                .thenThrow(new PaymentValidationException("Cannot transition from PENDING to REFUNDED"));

        PaymentStatusUpdateRequest request = new PaymentStatusUpdateRequest(PaymentStatus.REFUNDED);

        mockMvc.perform(patch("/api/v1/payments/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid payment"))
                .andExpect(jsonPath("$.detail").value("Cannot transition from PENDING to REFUNDED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/payments/{id}/status: missing payment returns 404")
    void patchMissingPaymentReturns404() throws Exception {
        when(paymentService.transitionStatus(99L, PaymentStatus.COMPLETED))
                .thenThrow(new PaymentNotFoundException(99L));

        PaymentStatusUpdateRequest request = new PaymentStatusUpdateRequest(PaymentStatus.COMPLETED);

        mockMvc.perform(patch("/api/v1/payments/99/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Payment not found"))
                .andExpect(jsonPath("$.detail").value("Payment not found: 99"));
    }

    @Test
    @DisplayName("PATCH /api/v1/payments/{id}/status: invalid status value returns 400")
    void patchInvalidStatusValueReturns400() throws Exception {
        String invalidJson = "{\"status\":\"INVALID_STATUS\"}";

        mockMvc.perform(patch("/api/v1/payments/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request body"));
    }

    /**
     * Helper to set private fields via reflection for test setup.
     */
    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
