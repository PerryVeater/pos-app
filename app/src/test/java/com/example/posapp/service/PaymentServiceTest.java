package com.example.posapp.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

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

import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.OrderNotFoundException;
import com.example.posapp.exception.PaymentNotFoundException;
import com.example.posapp.exception.PaymentValidationException;
import com.example.posapp.repository.PaymentRepository;

/**
 * Unit tests for the store-scoped {@link PaymentService} business rules.
 * <p>
 * The {@link PaymentRepository} and {@link OrderService} are mocked, so these
 * exercise the service in isolation: amount validation, the delegated
 * store-scoped order lookup (missing and foreign orders are refused identically
 * by {@link OrderService#getOrder}), cancelled-order rejection, ownership
 * enforcement through the payment's order, and the full transition matrix. No
 * Spring context or database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final long STORE_ID = 5L;

    @Mock
    private PaymentRepository paymentRepo;

    @Mock
    private OrderService orderService;

    @InjectMocks
    private PaymentService paymentService;

    /** A plain order with no store wiring, for the creation path which only
     * reads the status and attaches the order to the new payment. */
    private static Order plainOrder(OrderStatus status) {
        return new Order(status, DiningOption.DINE_IN, Instant.now());
    }

    /** A payment whose owning order resolves to a store with {@link #STORE_ID},
     * as the ownership check in get/transition expects. */
    private Payment ownedPayment(PaymentStatus status) {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(STORE_ID);
        Order order = new Order(OrderStatus.PENDING, DiningOption.DINE_IN, Instant.now());
        order.setStore(store);
        return new Payment(order, new BigDecimal("25.00"), PaymentMethod.CARD,
                status, Instant.now());
    }

    // --- createPayment ---

    @Test
    @DisplayName("createPayment: resolves the order store-scoped and saves a PENDING payment")
    void createPaymentSavesPayment() {
        when(orderService.getOrder(1L, STORE_ID)).thenReturn(plainOrder(OrderStatus.PENDING));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPayment(
                1L, new BigDecimal("25.00"), PaymentMethod.CARD, STORE_ID);

        assertThat(result.getAmount()).isEqualByComparingTo("25.00");
        assertThat(result.getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getCreatedAt()).isNotNull();
        verify(orderService).getOrder(1L, STORE_ID);
        verify(paymentRepo).save(any(Payment.class));
    }

    @Test
    @DisplayName("createPayment: a missing or foreign order propagates the store-scoped not-found (404)")
    void createPaymentPropagatesOrderNotFound() {
        when(orderService.getOrder(99L, STORE_ID)).thenThrow(new OrderNotFoundException(99L));

        assertThatThrownBy(() -> paymentService.createPayment(
                99L, new BigDecimal("10.00"), PaymentMethod.CASH, STORE_ID))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found: 99");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("createPayment: supports CASH method")
    void createPaymentCashMethod() {
        when(orderService.getOrder(1L, STORE_ID)).thenReturn(plainOrder(OrderStatus.CONFIRMED));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPayment(
                1L, new BigDecimal("50.00"), PaymentMethod.CASH, STORE_ID);

        assertThat(result.getMethod()).isEqualTo(PaymentMethod.CASH);
    }

    @Test
    @DisplayName("createPayment: supports OTHER method")
    void createPaymentOtherMethod() {
        when(orderService.getOrder(1L, STORE_ID)).thenReturn(plainOrder(OrderStatus.COMPLETED));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.createPayment(
                1L, new BigDecimal("75.00"), PaymentMethod.OTHER, STORE_ID);

        assertThat(result.getMethod()).isEqualTo(PaymentMethod.OTHER);
    }

    @Test
    @DisplayName("createPayment: rejects zero amount before any order lookup")
    void createPaymentRejectsZeroAmount() {
        assertThatThrownBy(() -> paymentService.createPayment(
                1L, BigDecimal.ZERO, PaymentMethod.CARD, STORE_ID))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Payment amount must be greater than zero");

        verify(orderService, never()).getOrder(any(), any());
        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("createPayment: rejects negative amount")
    void createPaymentRejectsNegativeAmount() {
        assertThatThrownBy(() -> paymentService.createPayment(
                1L, new BigDecimal("-5.00"), PaymentMethod.CARD, STORE_ID))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Payment amount must be greater than zero");

        verify(orderService, never()).getOrder(any(), any());
    }

    @Test
    @DisplayName("createPayment: rejects null amount")
    void createPaymentRejectsNullAmount() {
        assertThatThrownBy(() -> paymentService.createPayment(
                1L, null, PaymentMethod.CARD, STORE_ID))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Payment amount must be greater than zero");

        verify(orderService, never()).getOrder(any(), any());
    }

    @Test
    @DisplayName("createPayment: rejects a cancelled order")
    void createPaymentRejectsCancelledOrder() {
        when(orderService.getOrder(1L, STORE_ID)).thenReturn(plainOrder(OrderStatus.CANCELLED));

        assertThatThrownBy(() -> paymentService.createPayment(
                1L, new BigDecimal("25.00"), PaymentMethod.CARD, STORE_ID))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot create payment for a cancelled order: 1");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    // --- getPayment ---

    @Test
    @DisplayName("getPayment: returns the payment when its order belongs to the store")
    void getPaymentReturnsOwnedPayment() {
        Payment payment = ownedPayment(PaymentStatus.PENDING);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));

        assertThat(paymentService.getPayment(1L, STORE_ID)).isSameAs(payment);
    }

    @Test
    @DisplayName("getPayment: throws PaymentNotFoundException when the payment is missing")
    void getPaymentThrowsWhenMissing() {
        when(paymentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(99L, STORE_ID))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found: 99");
    }

    @Test
    @DisplayName("getPayment: a payment whose order is owned by another store is indistinguishable from missing")
    void getPaymentHidesCrossStore() {
        Payment owned = ownedPayment(PaymentStatus.PENDING);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> paymentService.getPayment(1L, 6L))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found: 1");
    }

    @Test
    @DisplayName("getPayment: a null id is rejected as a bad request")
    void getPaymentRejectsNullId() {
        assertThatThrownBy(() -> paymentService.getPayment(null, STORE_ID))
                .isInstanceOf(IllegalArgumentException.class);

        verify(paymentRepo, never()).findById(any());
    }

    // --- transitionStatus: allowed transitions ---

    @Test
    @DisplayName("transitionStatus: PENDING → COMPLETED is allowed")
    void transitionPendingToCompleted() {
        Payment payment = ownedPayment(PaymentStatus.PENDING);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.transitionStatus(1L, STORE_ID, PaymentStatus.COMPLETED);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        verify(paymentRepo).save(payment);
    }

    @Test
    @DisplayName("transitionStatus: PENDING → FAILED is allowed")
    void transitionPendingToFailed() {
        Payment payment = ownedPayment(PaymentStatus.PENDING);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.transitionStatus(1L, STORE_ID, PaymentStatus.FAILED);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(paymentRepo).save(payment);
    }

    @Test
    @DisplayName("transitionStatus: COMPLETED → REFUNDED is allowed")
    void transitionCompletedToRefunded() {
        Payment payment = ownedPayment(PaymentStatus.COMPLETED);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(payment));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.transitionStatus(1L, STORE_ID, PaymentStatus.REFUNDED);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentRepo).save(payment);
    }

    // --- transitionStatus: rejected transitions ---

    @Test
    @DisplayName("transitionStatus: PENDING → PENDING is rejected")
    void transitionPendingToPendingRejected() {
        Payment owned = ownedPayment(PaymentStatus.PENDING);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, STORE_ID, PaymentStatus.PENDING))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from PENDING to PENDING");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: PENDING → REFUNDED is rejected")
    void transitionPendingToRefundedRejected() {
        Payment owned = ownedPayment(PaymentStatus.PENDING);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, STORE_ID, PaymentStatus.REFUNDED))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from PENDING to REFUNDED");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: COMPLETED → PENDING is rejected")
    void transitionCompletedToPendingRejected() {
        Payment owned = ownedPayment(PaymentStatus.COMPLETED);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, STORE_ID, PaymentStatus.PENDING))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from COMPLETED to PENDING");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: FAILED → anything is rejected")
    void transitionFailedToAnythingRejected() {
        Payment owned = ownedPayment(PaymentStatus.FAILED);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, STORE_ID, PaymentStatus.COMPLETED))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from FAILED to COMPLETED");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: REFUNDED → anything is rejected")
    void transitionRefundedToAnythingRejected() {
        Payment owned = ownedPayment(PaymentStatus.REFUNDED);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, STORE_ID, PaymentStatus.PENDING))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from REFUNDED to PENDING");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: a nonexistent payment throws PaymentNotFoundException")
    void transitionMissingPaymentThrowsNotFound() {
        when(paymentRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.transitionStatus(99L, STORE_ID, PaymentStatus.COMPLETED))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found: 99");

        verify(paymentRepo, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("transitionStatus: a payment owned by another store is indistinguishable from missing")
    void transitionCrossStoreHidesPayment() {
        Payment owned = ownedPayment(PaymentStatus.PENDING);
        when(paymentRepo.findById(1L)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> paymentService.transitionStatus(1L, 6L, PaymentStatus.COMPLETED))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found: 1");

        verify(paymentRepo, never()).save(any(Payment.class));
    }
}
