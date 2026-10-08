package com.example.posapp.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.exception.PaymentValidationException;
import com.example.posapp.repository.OrderRepository;
import com.example.posapp.repository.PaymentRepository;

/**
 * Service layer for creating and retrieving {@link Payment}s.
 * <p>
 * Encapsulates the business rules:
 * <ul>
 *   <li>A payment must reference an existing order.</li>
 *   <li>Payments cannot be created for {@link OrderStatus#CANCELLED} orders.</li>
 *   <li>Payment amount must be greater than zero.</li>
 *   <li>The payment starts in {@link PaymentStatus#PENDING} status.</li>
 * </ul>
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
     * The payment starts in {@link PaymentStatus#PENDING} status. Business
     * rules enforced:
     * <ul>
     *   <li>Amount must be greater than zero.</li>
     *   <li>Order must exist.</li>
     *   <li>Order must not be {@link OrderStatus#CANCELLED}.</li>
     * </ul>
     * </p>
     *
     * @param orderId the ID of the order to pay for
     * @param amount the payment amount; must be positive
     * @param method the payment method
     * @return the saved {@link Payment}
     * @throws PaymentValidationException if the amount is not positive, the
     *         order does not exist, or the order is cancelled
     */
    public Payment createPayment(Long orderId, BigDecimal amount, PaymentMethod method) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentValidationException(
                    "Payment amount must be greater than zero");
        }

        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new PaymentValidationException(
                        "Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new PaymentValidationException(
                    "Cannot create payment for a cancelled order: " + orderId);
        }

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
