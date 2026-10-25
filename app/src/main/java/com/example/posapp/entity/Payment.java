package com.example.posapp.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Represents a payment for an {@link Order}.
 * <p>
 * A payment captures the amount paid, the payment method, and the current
 * status of the payment. Each payment is linked to exactly one order.
 * </p>
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic-locking counter (Flyway V23). A payment status moves
     * PENDING -> COMPLETED/FAILED and COMPLETED -> REFUNDED, and two terminals
     * can race those moves; the version makes the loser fail instead of
     * overwriting the winner. Mapped as a primitive so a new payment starts at
     * zero without a null check anywhere.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_payment_order"))
    private Order order;

    @Column(name = "amount", precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * Default constructor for JPA.
     */
    public Payment() {}

    /**
     * Constructor for creating a new payment.
     * @param order the order this payment is for
     * @param amount the payment amount
     * @param method the payment method
     * @param status the initial payment status
     * @param createdAt the creation timestamp
     */
    public Payment(Order order, BigDecimal amount, PaymentMethod method,
                   PaymentStatus status, LocalDateTime createdAt) {
        this.order = order;
        this.amount = amount;
        this.method = method;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    /**
     * Get the optimistic-locking version of the payment.
     * @return the version, zero for a payment that has never been updated
     */
    public long getVersion() {
        return version;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
