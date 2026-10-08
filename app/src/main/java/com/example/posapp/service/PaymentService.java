package com.example.posapp.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.Order;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.repository.OrderRepository;
import com.example.posapp.repository.PaymentRepository;

/**
 * Service layer for creating and retrieving {@link Payment}s.
 * <p>
 * Encapsulates the business rule that a payment must reference an existing
 * order. The payment starts in {@link PaymentStatus#PENDING} status.
 * </p>
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepo;
    private final OrderRepository orderRepo;

    /**
     * Constructor for PaymentService.
     * @param paymentRepo the repository for {@link Payment}s
     * @param orderRepo the repository for {@link Order}s
     */
    public PaymentService(PaymentRepository paymentRepo, OrderRepository orderRepo) {
        this.paymentRepo = paymentRepo;
        this.orderRepo = orderRepo;
    }

    /**
     * Create a new {@link Payment} for the given order.
     * <p>
     * The payment starts in {@link PaymentStatus#PENDING} status. The order
     * must exist; otherwise an {@link IllegalArgumentException} is thrown.
     * </p>
     *
     * @param orderId the ID of the order to pay for
     * @param amount the payment amount
     * @param method the payment method
     * @return the saved {@link Payment}
     * @throws IllegalArgumentException if the order does not exist
     */
    public Payment createPayment(Long orderId, BigDecimal amount, PaymentMethod method) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Order not found: " + orderId));

        Payment payment = new Payment(
                order, amount, method, PaymentStatus.PENDING, LocalDateTime.now());
        return paymentRepo.save(payment);
    }

    /**
     * Retrieve a {@link Payment} by its ID.
     * Returns {@code Optional.empty()} if the {@link Payment} does not exist.
     *
     * @param id the ID of the {@link Payment} to retrieve
     * @return an Optional containing the {@link Payment} if found, or empty if not
     */
    public Optional<Payment> getPaymentById(Long id) {
        return paymentRepo.findById(id);
    }
}
