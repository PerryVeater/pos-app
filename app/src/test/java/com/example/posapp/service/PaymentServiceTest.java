package com.example.posapp.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.exception.PaymentNotFoundException;
import com.example.posapp.exception.PaymentValidationException;
import com.example.posapp.repository.OrderRepository;
import com.example.posapp.repository.PaymentRepository;

/**
 * Unit tests for the {@link PaymentService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service layer in
 * isolation: order existence checks, payment creation, and delegation to the
 * repositories. No Spring context or database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepo;

    @Mock
    private OrderRepository orderRepo;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("createPayment: saves and returns a payment with PENDING status")
    void createPaymentSavesPayment() {
        Order order = new Order(OrderStatus.PENDING, LocalDateTime.now());
        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPayment(1L, new BigDecimal("25.00"), PaymentMethod.CARD);

        assertThat(result.getOrder()).isEqualTo(order);
        assertThat(result.getAmount()).isEqualByComparingTo("25.00");
        assertThat(result.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getCreatedAt()).isNotNull();
        verify(paymentRepo).save(any(Payment.class));
    }

    @Test
    @DisplayName("createPayment: throws PaymentValidationException when order does not exist")
    void createPaymentThrowsWhenOrderNotFound() {
        when(orderRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.createPayment(99L, new BigDecimal("10.00"), PaymentMethod.CASH))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Order not found: 99");
    }

    @Test
    @DisplayName("createPayment: supports CASH payment method")
    void createPaymentCashMethod() {
        Order order = new Order(OrderStatus.CONFIRMED, LocalDateTime.now());
        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPayment(1L, new BigDecimal("50.00"), PaymentMethod.CASH);

        assertThat(result.getMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    @DisplayName("createPayment: supports OTHER payment method")
    void createPaymentOtherMethod() {
        Order order = new Order(OrderStatus.COMPLETED, LocalDateTime.now());
        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPayment(1L, new BigDecimal("75.00"), PaymentMethod.OTHER);

        assertThat(result.getMethod()).isEqualTo(PaymentMethod.OTHER);
    }

    @Test
    @DisplayName("getPaymentById: returns the payment when it exists")
    void getPaymentByIdReturnsExistingPayment() {
        Payment existing = new Payment(
                new Order(OrderStatus.PENDING, LocalDateTime.now()),
                new BigDecimal("20.00"),
                PaymentMethod.CARD,
                PaymentStatus.PENDING,
                LocalDateTime.now());
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(paymentService.getPaymentById(1L)).contains(existing);
    }

    @Test
    @DisplayName("getPaymentById: returns empty when the payment does not exist")
    void getPaymentByIdReturnsEmptyForMissingPayment() {
        when(paymentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(paymentService.getPaymentById(99L)).isEmpty();
    }

    // --- Payment validation rules ---

    @Test
    @DisplayName("createPayment: rejects zero amount")
    void createPaymentRejectsZeroAmount() {
        assertThatThrownBy(() ->
                paymentService.createPayment(1L, BigDecimal.ZERO, PaymentMethod.CARD))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Payment amount must be greater than zero");
    }

    @Test
    @DisplayName("createPayment: rejects negative amount")
    void createPaymentRejectsNegativeAmount() {
        assertThatThrownBy(() ->
                paymentService.createPayment(1L, new BigDecimal("-5.00"), PaymentMethod.CARD))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Payment amount must be greater than zero");
    }

    @Test
    @DisplayName("createPayment: rejects null amount")
    void createPaymentRejectsNullAmount() {
        assertThatThrownBy(() ->
                paymentService.createPayment(1L, null, PaymentMethod.CARD))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Payment amount must be greater than zero");
    }

    @Test
    @DisplayName("createPayment: rejects cancelled order")
    void createPaymentRejectsCancelledOrder() {
        Order order = new Order(OrderStatus.CANCELLED, LocalDateTime.now());
        when(orderRepo.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                paymentService.createPayment(1L, new BigDecimal("25.00"), PaymentMethod.CARD))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot create payment for a cancelled order: 1");
    }

    // --- transitionStatus: allowed transitions ---

    @Test
    @DisplayName("transitionStatus: PENDING → COMPLETED is allowed")
    void transitionStatusPendingToCompleted() {
        Payment payment = new Payment(
                new Order(OrderStatus.PENDING, LocalDateTime.now()),
                new BigDecimal("25.00"), PaymentMethod.CARD,
                PaymentStatus.PENDING, LocalDateTime.now());
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.transitionStatus(1L, PaymentStatus.COMPLETED);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepo).save(payment);
    }

    @Test
    @DisplayName("transitionStatus: PENDING → FAILED is allowed")
    void transitionStatusPendingToFailed() {
        Payment payment = new Payment(
                new Order(OrderStatus.PENDING, LocalDateTime.now()),
                new BigDecimal("25.00"), PaymentMethod.CARD,
                PaymentStatus.PENDING, LocalDateTime.now());
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.transitionStatus(1L, PaymentStatus.FAILED);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepo).save(payment);
    }

    @Test
    @DisplayName("transitionStatus: COMPLETED → REFUNDED is allowed")
    void transitionStatusCompletedToRefunded() {
        Payment payment = new Payment(
                new Order(OrderStatus.PENDING, LocalDateTime.now()),
                new BigDecimal("25.00"), PaymentMethod.CARD,
                PaymentStatus.COMPLETED, LocalDateTime.now());
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.transitionStatus(1L, PaymentStatus.REFUNDED);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepo).save(payment);
    }

    // --- transitionStatus: rejected transitions ---

    @Test
    @DisplayName("transitionStatus: PENDING → PENDING is rejected")
    void transitionStatusPendingToPendingRejected() {
        Payment payment = new Payment(
                new Order(OrderStatus.PENDING, LocalDateTime.now()),
                new BigDecimal("25.00"), PaymentMethod.CARD,
                PaymentStatus.PENDING, LocalDateTime.now());
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, PaymentStatus.PENDING))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from PENDING to PENDING");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: PENDING → REFUNDED is rejected")
    void transitionStatusPendingToRefundedRejected() {
        Payment payment = new Payment(
                new Order(OrderStatus.PENDING, LocalDateTime.now()),
                new BigDecimal("25.00"), PaymentMethod.CARD,
                PaymentStatus.PENDING, LocalDateTime.now());
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, PaymentStatus.REFUNDED))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from PENDING to REFUNDED");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: COMPLETED → PENDING is rejected")
    void transitionStatusCompletedToPendingRejected() {
        Payment payment = new Payment(
                new Order(OrderStatus.PENDING, LocalDateTime.now()),
                new BigDecimal("25.00"), PaymentMethod.CARD,
                PaymentStatus.COMPLETED, LocalDateTime.now());
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, PaymentStatus.PENDING))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from COMPLETED to PENDING");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: FAILED → anything is rejected")
    void transitionStatusFailedToAnythingRejected() {
        Payment payment = new Payment(
                new Order(OrderStatus.PENDING, LocalDateTime.now()),
                new BigDecimal("25.00"), PaymentMethod.CARD,
                PaymentStatus.FAILED, LocalDateTime.now());
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, PaymentStatus.COMPLETED))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from FAILED to COMPLETED");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: REFUNDED → anything is rejected")
    void transitionStatusRefundedToAnythingRejected() {
        Payment payment = new Payment(
                new Order(OrderStatus.PENDING, LocalDateTime.now()),
                new BigDecimal("25.00"), PaymentMethod.CARD,
                PaymentStatus.REFUNDED, LocalDateTime.now());
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, PaymentStatus.PENDING))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from REFUNDED to PENDING");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: nonexistent payment throws PaymentNotFoundException")
    void transitionStatusNonexistentPaymentThrowsNotFound() {
        when(paymentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.transitionStatus(99L, PaymentStatus.COMPLETED))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessageContaining("Payment not found: 99");

        verify(paymentRepo, never()).save(any(Payment.class));
    }
}
