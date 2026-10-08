package com.example.posapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.Order;

/**
 * Repository interface for managing {@link Order} entities.
 * <p>
 * This interface extends {@code JpaRepository}, which provides CRUD operations
 * and query methods for {@link Order} entities.
 * </p>
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Called by {@link com.example.posapp.service.OrderService} to persist
 *       and retrieve orders.</li>
 * </ul>
 * </p>
 *
 * @see com.example.posapp.entity.Order
 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
