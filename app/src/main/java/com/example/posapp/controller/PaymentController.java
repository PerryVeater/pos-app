package com.example.posapp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.PaymentRequest;
import com.example.posapp.dto.PaymentResponse;
import com.example.posapp.entity.Payment;
import com.example.posapp.exception.PaymentNotFoundException;
import com.example.posapp.service.PaymentService;

/**
 * REST controller for payment operations.
 * <p>
 * Provides endpoints for creating and retrieving payments. Business rules
 * are enforced by the {@link PaymentService}.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Constructor for PaymentController.
     * @param paymentService the service for payment operations
     */
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Create a new payment.
     * <p>
     * Delegates to {@link PaymentService#createPayment} which enforces business
     * rules: amount must be positive, order must exist, order must not be
     * cancelled. Returns HTTP 201 Created with the payment response DTO.
     * </p>
     *
     * @param request the payment creation request
     * @return the created payment as a response DTO
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@RequestBody PaymentRequest request) {
        Payment payment = paymentService.createPayment(
                request.orderId(), request.amount(), request.method());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentResponse.from(payment));
    }

    /**
     * Retrieve a payment by ID.
     * <p>
     * Returns HTTP 200 with the payment response DTO when found, or throws
     * {@link PaymentNotFoundException} (mapped to 404 by the exception handler)
     * when the payment does not exist.
     * </p>
     *
     * @param id the payment ID
     * @return the payment as a response DTO
     * @throws PaymentNotFoundException if the payment does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id) {
        Payment payment = paymentService.getPaymentById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return ResponseEntity.ok(PaymentResponse.from(payment));
    }
}
