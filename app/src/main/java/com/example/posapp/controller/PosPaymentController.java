package com.example.posapp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.PaymentRequest;
import com.example.posapp.dto.PaymentResponse;
import com.example.posapp.dto.PaymentStatusUpdateRequest;
import com.example.posapp.entity.Payment;
import com.example.posapp.service.PosPaymentService;

/**
 * Authenticated, store-scoped POS payment endpoints.
 * <p>
 * All three require an {@code Authorization: Bearer <token>} header issued by
 * POS authentication. The store a payment is confined to is derived
 * exclusively from that session - the request body never carries a store
 * identity - and ownership is enforced through the payment's order, so one store
 * can neither pay for, read, nor transition another store's orders or payments. A
 * missing or malformed header is passed through as {@code null} (so it is refused
 * as an invalid session with 401, never as a 400), and a resource that is absent
 * or owned by another store is indistinguishable from a non-existent one (404).
 * </p>
 * <p>
 * This is the sole HTTP interface for payments. The legacy unauthenticated
 * {@code /api/v1/payments} endpoints have been removed; every payment is created,
 * read, and transitioned through this session-bound API.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/pos/payments")
public class PosPaymentController {

    private final PosPaymentService posPaymentService;

    /**
     * Constructor for PosPaymentController.
     * @param posPaymentService the session-bound payment service
     */
    public PosPaymentController(PosPaymentService posPaymentService) {
        this.posPaymentService = posPaymentService;
    }

    /**
     * Create a payment for an order owned by the session's store.
     * @param authorization the raw {@code Authorization} header, possibly absent
     * @param request the payment request (order id, amount, method)
     * @return 201 with the created payment
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody PaymentRequest request) {
        Payment payment = posPaymentService.createPayment(authorization, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(PaymentResponse.from(payment));
    }

    /**
     * Retrieve a payment whose order belongs to the session's store.
     * @param authorization the raw {@code Authorization} header, possibly absent
     * @param id the payment ID
     * @return 200 with the payment, or 404 when it is missing/other-store
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        Payment payment = posPaymentService.getPayment(authorization, id);
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }

    /**
     * Transition the status of a payment whose order belongs to the session's store.
     * @param authorization the raw {@code Authorization} header, possibly absent
     * @param id the payment ID
     * @param request the status update request
     * @return 200 with the updated payment, or 404 when it is missing/other-store
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<PaymentResponse> updatePaymentStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody PaymentStatusUpdateRequest request) {
        Payment payment = posPaymentService.updateStatus(authorization, id, request.status());
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }
}
