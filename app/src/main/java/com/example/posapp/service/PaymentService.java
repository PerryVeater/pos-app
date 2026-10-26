package com.example.posapp.service;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.exception.PaymentNotFoundException;
import com.example.posapp.exception.PaymentValidationException;
import com.example.posapp.repository.PaymentRepository;

/**
 * Service layer for creating, retrieving, and transitioning {@link Payment}s.
 * <p>
 * Encapsulates the business rules:
 * <ul>
 *   <li>A payment must reference an existing order owned by the caller's store.</li>
 *   <li>Payments cannot be created for {@link OrderStatus#CANCELLED} orders.</li>
 *   <li>Payment amount must be greater than zero.</li>
 *   <li>The payment starts in {@link PaymentStatus#PENDING} status.</li>
 *   <li>Status transitions are enforced (see {@link #transitionStatus}).</li>
 *   <li>Multiple payments per order are permitted (no dedup or total matching).</li>
 * </ul>
 * </p>
 * <p>
 * Every operation is scoped to a store so one store can never observe or mutate
 * another store's payments. A payment carries no store of its own: its tenancy
 * is derived through the owning order, so ownership is enforced through the
 * {@code Payment -> Order -> Store} path. The store is supplied by the caller
 * ({@link PosPaymentService}, which reads it from the authenticated session) and
 * is never taken from the request. A missing payment and a payment whose order
 * belongs to a different store raise the same {@link PaymentNotFoundException},
 * so the response never discloses that the resource exists elsewhere; for
 * creation the order lookup is delegated to {@link OrderService#getOrder} which
 * gives the identical treatment to a missing or foreign order.
 * </p>
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final OrderService orderService;

    /**
     * Constructor for PaymentService.
     * @param paymentRepo the repository for {@link Payment}s
     * @param orderService the order service, reused for the store-scoped
     *        order lookup that gives missing and foreign orders the same
     *        not-found response
     */
    public PaymentService(PaymentRepository paymentRepo, OrderService orderService) {
        this.paymentRepo = paymentRepo;
        this.orderService = orderService;
    }

    /**
     * Create a new {@link Payment} for an order that belongs to the given store.
     * <p>
     * The payment starts in {@link PaymentStatus#PENDING} status. Business
     * rules enforced:
     * <ul>
     *   <li>Amount must be greater than zero.</li>
     *   <li>Order must exist and be owned by {@code storeId}.</li>
     *   <li>Order must not be {@link OrderStatus#CANCELLED}.</li>
     * </ul>
     * The order is resolved through the store-scoped
     * {@link OrderService#getOrder(Long, Long)} so a nonexistent order and one
     * owned by another store are refused identically (no existence disclosure).
     * </p>
     *
     * @param orderId the ID of the order to pay for
     * @param amount the payment amount; must be positive
     * @param method the payment method
     * @param storeId the authenticated session's store ID
     * @return the saved {@link Payment}
     * @throws com.example.posapp.exception.OrderNotFoundException if the order
     *         is missing or owned by another store (identical response in both
     *         cases)
     * @throws PaymentValidationException if the amount is not positive or the
     *         order is cancelled
     */
    @Transactional
    public Payment createPayment(Long orderId, BigDecimal amount, PaymentMethod method,
                                 Long storeId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentValidationException(
                    "Payment amount must be greater than zero");
        }

        Order order = orderService.getOrder(orderId, storeId);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentValidationException(
                    "Cannot create payment for a cancelled order: " + orderId);
        }

        Payment payment = new Payment(
                order, amount, method, PaymentStatus.PENDING, Instant.now());
        return paymentRepo.save(payment);
    }

    /**
     * Retrieve a {@link Payment} by ID, but only if its order belongs to the
     * given store. A nonexistent payment and one owned by another store both
     * raise the same {@link PaymentNotFoundException}.
     *
     * @param id the payment ID
     * @param storeId the authenticated session's store ID
     * @return the payment, guaranteed to be owned by {@code storeId}
     * @throws IllegalArgumentException if {@code id} is null
     * @throws PaymentNotFoundException if the payment is missing or its order
     *         is owned by another store
     */
    @Transactional(readOnly = true)
    public Payment getPayment(Long id, Long storeId) {
        if (id == null) {
            throw new IllegalArgumentException("Payment id must not be null");
        }
        Payment payment = paymentRepo.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        if (payment.getOrder().getStore() == null
                || !payment.getOrder().getStore().getId().equals(storeId)) {
            throw new PaymentNotFoundException(id);
        }
        return payment;
    }

    /**
     * Transition a {@link Payment} to a new status, but only if its order
     * belongs to the given store.
     * <p>
     * Allowed transitions:
     * <ul>
     *   <li>{@link PaymentStatus#PENDING} → {@link PaymentStatus#COMPLETED}</li>
     *   <li>{@link PaymentStatus#PENDING} → {@link PaymentStatus#FAILED}</li>
     *   <li>{@link PaymentStatus#COMPLETED} → {@link PaymentStatus#REFUNDED}</li>
     * </ul>
     * Terminal states ({@link PaymentStatus#FAILED}, {@link PaymentStatus#REFUNDED})
     * cannot be transitioned further. All other transitions are rejected with
     * {@link PaymentValidationException}.
     * </p>
     * <p>
     * The lookup, ownership check, transition validation, and write run inside
     * a single transaction so the payment cannot change hands between the check
     * and the write. The same generic {@link PaymentNotFoundException} used by
     * {@link #getPayment} is raised for a missing or other-store payment, so
     * this never discloses that the payment exists elsewhere.
     * </p>
     *
     * @param paymentId the ID of the payment to transition
     * @param storeId the authenticated session's store ID
     * @param newStatus the target status
     * @return the updated {@link Payment}
     * @throws PaymentNotFoundException if the payment is missing or its order is
     *         owned by another store
     * @throws PaymentValidationException if the transition is not allowed
     */
    @Transactional
    public Payment transitionStatus(Long paymentId, Long storeId, PaymentStatus newStatus) {
        Payment payment = paymentRepo.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        if (payment.getOrder().getStore() == null
                || !payment.getOrder().getStore().getId().equals(storeId)) {
            throw new PaymentNotFoundException(paymentId);
        }

        PaymentStatus currentStatus = payment.getStatus();
        if (!isAllowedTransition(currentStatus, newStatus)) {
            throw new PaymentValidationException(
                    "Cannot transition from " + currentStatus + " to " + newStatus);
        }

        payment.setStatus(newStatus);
        return paymentRepo.save(payment);
    }

    /**
     * Check whether a status transition is allowed.
     * @param from the current status
     * @param to the target status
     * @return {@code true} if the transition is allowed, {@code false} otherwise
     */
    private boolean isAllowedTransition(PaymentStatus from, PaymentStatus to) {
        return switch (from) {
            case PENDING -> to == PaymentStatus.COMPLETED || to == PaymentStatus.FAILED;
            case COMPLETED -> to == PaymentStatus.REFUNDED;
            case FAILED, REFUNDED -> false;
        };
    }
}
