package com.example.posapp.service;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.dto.PaymentRequest;
import com.example.posapp.dto.PosSessionResponse;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.exception.PosSessionInvalidException;

/**
 * Unit tests for {@link PosPaymentService}: the store scoping always comes from
 * the resolved session (never the request), the existing {@link PaymentService}
 * is reused for the actual payment work, and an invalid session short-circuits
 * before any payment operation runs.
 */
@ExtendWith(MockitoExtension.class)
class PosPaymentServiceTest {

    @Mock
    private PosSessionService sessionService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PosPaymentService posPaymentService;

    private static PosSessionResponse session(long storeId, long employeeId) {
        return new PosSessionResponse(employeeId, "Ada", storeId, "Downtown",
                Instant.parse("2026-10-14T20:00:00Z"));
    }

    private static PaymentRequest request() {
        return new PaymentRequest(7L, new BigDecimal("10.00"), PaymentMethod.CARD);
    }

    @Test
    @DisplayName("createPayment scopes to the session's store and reuses PaymentService")
    void createPaymentScopesToSessionStore() {
        when(sessionService.currentSession("Bearer tok")).thenReturn(session(2L, 1L));
        Payment payment = mock(Payment.class);
        when(paymentService.createPayment(7L, new BigDecimal("10.00"), PaymentMethod.CARD, 2L))
                .thenReturn(payment);

        Payment result = posPaymentService.createPayment("Bearer tok", request());

        assertThat(result).isSameAs(payment);
        verify(paymentService).createPayment(7L, new BigDecimal("10.00"), PaymentMethod.CARD, 2L);
    }

    @Test
    @DisplayName("createPayment: an invalid session never reaches the payment service")
    void createPaymentRejectsInvalidSession() {
        when(sessionService.currentSession("Bearer dead"))
                .thenThrow(new PosSessionInvalidException());

        assertThatThrownBy(() -> posPaymentService.createPayment("Bearer dead", request()))
                .isInstanceOf(PosSessionInvalidException.class);

        verify(paymentService, never()).createPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("createPayment: a missing header is treated as an invalid session")
    void createPaymentRejectsMissingHeader() {
        when(sessionService.currentSession(null))
                .thenThrow(new PosSessionInvalidException());

        assertThatThrownBy(() -> posPaymentService.createPayment(null, request()))
                .isInstanceOf(PosSessionInvalidException.class);

        verify(paymentService, never()).createPayment(any(), any(), any(), any());
    }

    @Test
    @DisplayName("getPayment scopes retrieval to the session's store")
    void getPaymentScopesToSessionStore() {
        when(sessionService.currentSession("Bearer tok")).thenReturn(session(2L, 1L));
        Payment payment = mock(Payment.class);
        when(paymentService.getPayment(5L, 2L)).thenReturn(payment);

        assertThat(posPaymentService.getPayment("Bearer tok", 5L)).isSameAs(payment);
    }

    @Test
    @DisplayName("getPayment: a revoked session never reaches the payment service")
    void getPaymentRejectsRevokedSession() {
        when(sessionService.currentSession("Bearer revoked"))
                .thenThrow(new PosSessionInvalidException());

        assertThatThrownBy(() -> posPaymentService.getPayment("Bearer revoked", 5L))
                .isInstanceOf(PosSessionInvalidException.class);

        verify(paymentService, never()).getPayment(any(), any());
    }

    @Test
    @DisplayName("updateStatus scopes the transition to the session's store")
    void updateStatusScopesToSessionStore() {
        when(sessionService.currentSession("Bearer tok")).thenReturn(session(2L, 1L));
        Payment payment = mock(Payment.class);
        when(paymentService.transitionStatus(5L, 2L, PaymentStatus.COMPLETED))
                .thenReturn(payment);

        Payment result = posPaymentService.updateStatus("Bearer tok", 5L, PaymentStatus.COMPLETED);

        assertThat(result).isSameAs(payment);
        verify(paymentService).transitionStatus(5L, 2L, PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("updateStatus: an invalid session never reaches the payment service")
    void updateStatusRejectsInvalidSession() {
        when(sessionService.currentSession(null))
                .thenThrow(new PosSessionInvalidException());

        assertThatThrownBy(() -> posPaymentService.updateStatus(null, 5L, PaymentStatus.COMPLETED))
                .isInstanceOf(PosSessionInvalidException.class);

        verify(paymentService, never()).transitionStatus(any(), any(), any());
    }
}
