package com.example.posapp.service;

import org.springframework.stereotype.Service;

import com.example.posapp.dto.PaymentRequest;
import com.example.posapp.dto.PosSessionResponse;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentStatus;

/**
 * Authenticated POS payment operations layered on the bearer-session foundation.
 * <p>
 * Every method first resolves the presented {@code Authorization} header through
 * {@link PosSessionService#currentSession(String)} - so a missing, malformed,
 * unknown, expired, or revoked token is refused with the same generic 401 as the
 * other POS endpoints, with no token logic duplicated here - and then derives the
 * store <em>exclusively</em> from that session. The client never supplies a
 * store: the request body carries only the order reference, amount, and method,
 * and the owning store is read from the resolved session before any payment work
 * runs.
 * </p>
 * <p>
 * Payment validation, ownership enforcement, and transition rules all live in
 * {@link PaymentService}; this class adds no payment logic of its own, it only
 * binds the session's store to it - mirroring {@link PosOrderService}.
 * Ownership is enforced through the payment's order (Payment -&gt; Order -&gt;
 * Store), so no separate store attribution is stored on the payment.
 * </p>
 */
@Service
public class PosPaymentService {

    private final PosSessionService sessionService;
    private final PaymentService paymentService;

    /**
     * Constructor for PosPaymentService.
     * @param sessionService the bearer-session resolver reused for every call
     * @param paymentService the existing payment business logic
     */
    public PosPaymentService(PosSessionService sessionService,
                             PaymentService paymentService) {
        this.sessionService = sessionService;
        this.paymentService = paymentService;
    }

    /**
     * Create a payment for an order owned by the session's store.
     * @param authorizationHeader the raw {@code Authorization} header
     * @param request the payment request (order id, amount, method)
     * @return the persisted payment
     * @throws com.example.posapp.exception.PosSessionInvalidException when the
     *         session is missing, malformed, unknown, expired, or revoked
     * @throws com.example.posapp.exception.OrderNotFoundException when the order
     *         is missing or owned by another store (identical response)
     * @throws com.example.posapp.exception.PaymentValidationException on a
     *         non-positive amount or a cancelled order
     */
    public Payment createPayment(String authorizationHeader, PaymentRequest request) {
        PosSessionResponse session = sessionService.currentSession(authorizationHeader);
        return paymentService.createPayment(
                request.orderId(), request.amount(), request.method(), session.storeId());
    }

    /**
     * Retrieve a payment, but only if its order belongs to the session's store.
     * @param authorizationHeader the raw {@code Authorization} header
     * @param paymentId the payment ID
     * @return the payment, guaranteed to be owned by the session's store
     * @throws com.example.posapp.exception.PosSessionInvalidException when the
     *         session is missing, malformed, unknown, expired, or revoked
     * @throws com.example.posapp.exception.PaymentNotFoundException when the
     *         payment is missing or its order is owned by another store
     *         (identical response in both cases)
     */
    public Payment getPayment(String authorizationHeader, Long paymentId) {
        PosSessionResponse session = sessionService.currentSession(authorizationHeader);
        return paymentService.getPayment(paymentId, session.storeId());
    }

    /**
     * Transition a payment's status, but only if its order belongs to the
     * session's store.
     * @param authorizationHeader the raw {@code Authorization} header
     * @param paymentId the payment ID
     * @param newStatus the target status
     * @return the updated payment
     * @throws com.example.posapp.exception.PosSessionInvalidException when the
     *         session is missing, malformed, unknown, expired, or revoked
     * @throws com.example.posapp.exception.PaymentNotFoundException when the
     *         payment is missing or its order is owned by another store
     *         (identical response in both cases)
     * @throws com.example.posapp.exception.PaymentValidationException on a
     *         disallowed transition (the standard transition rules)
     */
    public Payment updateStatus(String authorizationHeader, Long paymentId, PaymentStatus newStatus) {
        PosSessionResponse session = sessionService.currentSession(authorizationHeader);
        return paymentService.transitionStatus(paymentId, session.storeId(), newStatus);
    }
}
