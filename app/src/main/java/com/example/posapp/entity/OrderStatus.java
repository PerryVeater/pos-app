package com.example.posapp.entity;

/**
 * Lifecycle states for an {@link Order}.
 * <p>
 * Stored as a {@code VARCHAR} column via {@code @Enumerated(EnumType.STRING)}
 * so the database values are human-readable and survive enum reordering.
 * </p>
 */
public enum OrderStatus {
    /** Order has been created but not yet confirmed. */
    PENDING,
    /** Order has been confirmed and is being prepared. */
    CONFIRMED,
    /** Order has been completed. */
    COMPLETED,
    /** Order has been cancelled. */
    CANCELLED
}
