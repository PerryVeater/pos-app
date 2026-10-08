package com.example.posapp.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderLine;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.exception.OrderNotFoundException;
import com.example.posapp.exception.OrderValidationException;
import com.example.posapp.repository.OrderRepository;
import com.example.posapp.repository.MenuItemRepository;

/**
 * Service layer for creating and retrieving {@link Order}s.
 * <p>
 * Encapsulates the business rule that each line's unit price is captured
 * from the product's current price at order-creation time. Later changes
 * to a product's price do not affect existing order lines.
 * </p>
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Called by future order endpoints (not yet implemented) to process
 *       client requests.</li>
 *   <li>Ensures data integrity before interacting with the database.</li>
 * </ul>
 * </p>
 *
 * @see com.example.posapp.entity.Order
 * @see com.example.posapp.repository.OrderRepository
 */
@Service
public class OrderService {

    private final OrderRepository orderRepo;
    private final MenuItemRepository menuItemRepo;

    /**
     * Constructor for OrderService.
     * @param orderRepo the repository for {@link Order}s
     * @param menuItemRepo the repository for {@link MenuItem}s
     */
    public OrderService(OrderRepository orderRepo, MenuItemRepository menuItemRepo) {
        this.orderRepo = orderRepo;
        this.menuItemRepo = menuItemRepo;
    }

    /**
     * Create a new {@link Order} with the given dining option and line items.
     * <p>
     * Each line's unit price is captured from the referenced product's
     * current price at creation time. The order starts in
     * {@link OrderStatus#PENDING}.
     * </p>
     *
     * @param diningOption how the order will be consumed; must not be null
     * @param lineInputs the line items to include; each references a product
     *        by ID and a positive quantity
     * @return the saved {@link Order} with its lines
     * @throws OrderValidationException if the dining option is null, the list
     *         is null or empty, a product does not exist, a product is inactive,
     *         or a quantity is not positive
     */
    public Order createOrder(DiningOption diningOption, List<OrderLineInput> lineInputs) {
        if (diningOption == null) {
            throw new OrderValidationException("Dining option is required");
        }

        if (lineInputs == null || lineInputs.isEmpty()) {
            throw new OrderValidationException("Order must have at least one line");
        }

        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        order.setDiningOption(diningOption);
        order.setCreatedAt(LocalDateTime.now());

        for (OrderLineInput input : lineInputs) {
            MenuItem product = menuItemRepo.findById(input.productId())
                    .orElseThrow(() -> new OrderValidationException(
                            "Product not found: " + input.productId()));

            if (!product.isActive()) {
                throw new OrderValidationException(
                        "Product is not active: " + input.productId());
            }

            if (input.quantity() <= 0) {
                throw new OrderValidationException(
                        "Quantity must be positive for product: " + input.productId());
            }

            OrderLine line = new OrderLine(product, input.quantity(), product.getPrice());
            order.addLine(line);
        }

        return orderRepo.save(order);
    }

    /**
     * Retrieve an {@link Order} by its ID.
     * Returns {@code Optional.empty()} if the {@link Order} does not exist.
     *
     * @param id the ID of the {@link Order} to retrieve
     * @return an Optional containing the {@link Order} if found, or empty if not
     * @throws IllegalArgumentException if the ID is null
     */
    public Optional<Order> getOrderById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Order id must not be null");
        }
        return orderRepo.findById(id);
    }

    /**
     * Transition an {@link Order} to a new status.
     * <p>
     * Allowed transitions:
     * <ul>
     *   <li>{@link OrderStatus#PENDING} → {@link OrderStatus#CONFIRMED}</li>
     *   <li>{@link OrderStatus#PENDING} → {@link OrderStatus#CANCELLED}</li>
     *   <li>{@link OrderStatus#CONFIRMED} → {@link OrderStatus#COMPLETED}</li>
     *   <li>{@link OrderStatus#CONFIRMED} → {@link OrderStatus#CANCELLED}</li>
     * </ul>
     * All other transitions are rejected with {@link OrderValidationException}.
     * Terminal states ({@link OrderStatus#COMPLETED}, {@link OrderStatus#CANCELLED})
     * cannot be transitioned further.
     * </p>
     *
     * @param orderId the ID of the order to transition
     * @param newStatus the target status
     * @return the updated {@link Order}
     * @throws OrderNotFoundException if the order does not exist
     * @throws OrderValidationException if the transition is not allowed
     */
    public Order transitionStatus(Long orderId, OrderStatus newStatus) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus currentStatus = order.getStatus();
        if (!isAllowedTransition(currentStatus, newStatus)) {
            throw new OrderValidationException(
                    "Cannot transition from " + currentStatus + " to " + newStatus);
        }

        order.setStatus(newStatus);
        return orderRepo.save(order);
    }

    /**
     * Check whether a status transition is allowed.
     * @param from the current status
     * @param to the target status
     * @return {@code true} if the transition is allowed, {@code false} otherwise
     */
    private boolean isAllowedTransition(OrderStatus from, OrderStatus to) {
        return switch (from) {
            case PENDING -> to == OrderStatus.CONFIRMED || to == OrderStatus.CANCELLED;
            case CONFIRMED -> to == OrderStatus.COMPLETED || to == OrderStatus.CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    /**
     * Input record for a single line item when creating an order.
     *
     * @param productId the ID of the product to order
     * @param quantity the number of units to order; must be positive
     */
    public record OrderLineInput(Long productId, int quantity) {}
}
