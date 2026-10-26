package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Load an order together with its lines in a single statement.
     * <p>
     * {@code Order.lines} is lazy, so a plain {@code findById} leaves the
     * collection unloaded: reading it after the transaction has closed would
     * fail. The paths that turn an order into a response therefore go through
     * this query, which joins the lines eagerly and leaves the mapping code
     * free of session assumptions. The method name is explicit about the extra
     * work because the join is only worth it for one order, never for a list.
     * </p>
     * <p>
     * The item on each line and the order's store and employee are in the graph
     * too. {@code @EntityGraph} follows fetch-graph semantics, not load-graph
     * ones: every attribute it does not name is treated as lazy even where the
     * mapping says otherwise, so omitting them would hand back proxies that a
     * caller mapping a response cannot read. Naming them keeps the query honest
     * about what a response needs - three joins for one order.
     * </p>
     *
     * @param id the order ID
     * @return the order with its lines initialized, or empty when absent
     */
    @EntityGraph(attributePaths = {"lines", "lines.product", "store", "employee"})
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findWithLinesById(@Param("id") Long id);
}
