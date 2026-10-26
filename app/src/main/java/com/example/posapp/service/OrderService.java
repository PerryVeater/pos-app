package com.example.posapp.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderLine;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.OrderNotFoundException;
import com.example.posapp.exception.OrderValidationException;
import com.example.posapp.repository.OrderRepository;
import com.example.posapp.repository.MenuItemRepository;

/**
 * Service layer for creating and managing {@link Order}s.
 * <p>
 * Encapsulates the business rules that each line's unit price is captured from
 * the product's current price at order-creation time (later changes to a
 * product's price do not affect existing order lines) and that status changes
 * follow a fixed transition graph.
 * </p>
 * <p>
 * Every order is attributed to a store and an employee. Attribution is required
 * at creation and is always derived from the authenticated POS bearer session by
 * the caller ({@link PosOrderService}); the client never supplies it. Retrieval
 * and status updates are scoped to a store so one store can never observe or
 * mutate another store's orders. The legacy unauthenticated order API has been
 * removed, so these are the only order operations.
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
     * Create a new {@link Order} attributed to a store and employee.
     * <p>
     * Each line's unit price is captured from the referenced product's current
     * price at creation time. The order starts in {@link OrderStatus#PENDING}
     * and carries the supplied {@code store} and {@code employee}, which the
     * caller derives exclusively from the authenticated session - the client
     * never chooses them.
     * </p>
     *
     * @param diningOption how the order will be consumed; must not be null
     * @param lineInputs the line items to include; each references a product
     *        by ID and a positive quantity
     * @param store the owning store from the session; must not be null
     * @param employee the creating employee from the session; must not be null
     * @return the saved {@link Order} with its lines
     * @throws OrderValidationException if the dining option, store, or employee
     *         is null, the list is null or empty, a product does not exist, a
     *         product is inactive, or a quantity is not positive
     */
    public Order createOrder(DiningOption diningOption, List<OrderLineInput> lineInputs,
                             Store store, Employee employee) {
        if (diningOption == null) {
            throw new OrderValidationException("Dining option is required");
        }

        if (store == null || employee == null) {
            throw new OrderValidationException("Order attribution is required");
        }

        if (lineInputs == null || lineInputs.isEmpty()) {
            throw new OrderValidationException("Order must have at least one line");
        }

        Order order = new Order();
        order.setStatus(OrderStatus.PENDING);
        order.setDiningOption(diningOption);
        order.setCreatedAt(Instant.now());
        order.setStore(store);
        order.setEmployee(employee);

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
     * Retrieve an {@link Order} by ID, but only if it belongs to the given store.
     * <p>
     * The store is derived from the authenticated session rather than supplied
     * by the client. A nonexistent order and an order owned by a different store
     * both raise the same {@link OrderNotFoundException}, so the response never
     * discloses that the order exists at another store.
     * </p>
     *
     * @param id the order ID
     * @param storeId the authenticated session's store ID
     * @return the order, guaranteed to belong to {@code storeId}
     * @throws IllegalArgumentException if {@code id} is null
     * @throws OrderNotFoundException if the order is missing or owned by
     *         another store
     */
    public Order getOrder(Long id, Long storeId) {
        if (id == null) {
            throw new IllegalArgumentException("Order id must not be null");
        }
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        if (order.getStore() == null || !order.getStore().getId().equals(storeId)) {
            throw new OrderNotFoundException(id);
        }
        return order;
    }

    /**
     * Transition an {@link Order} to a new status, but only if the order
     * belongs to the given store.
     * <p>
     * Allowed transitions:
     * <ul>
     *   <li>{@link OrderStatus#PENDING} → {@link OrderStatus#CONFIRMED}</li>
     *   <li>{@link OrderStatus#PENDING} → {@link OrderStatus#CANCELLED}</li>
     *   <li>{@link OrderStatus#CONFIRMED} → {@link OrderStatus#COMPLETED}</li>
     *   <li>{@link OrderStatus#CONFIRMED} → {@link OrderStatus#CANCELLED}</li>
     * </ul>
     * Terminal states ({@link OrderStatus#COMPLETED}, {@link OrderStatus#CANCELLED})
     * cannot be transitioned further. All other transitions are rejected with
     * {@link OrderValidationException}.
     * </p>
     * <p>
     * The store-ownership check and the status update run inside a single
     * transaction so the order cannot change hands between the check and the
     * write - closing the time-of-check/time-of-use gap a separate read-then-write
     * would leave. The same generic {@link OrderNotFoundException} used by
     * {@link #getOrder} is raised for a missing or other-store order, so this
     * never discloses that the order exists elsewhere.
     * </p>
     *
     * @param orderId the order to transition
     * @param storeId the authenticated session's store ID
     * @param newStatus the target status
     * @return the updated {@link Order}
     * @throws OrderNotFoundException if the order is missing or owned by
     *         another store
     * @throws OrderValidationException if the transition is not allowed
     */
    @Transactional
    public Order transitionStatus(Long orderId, Long storeId, OrderStatus newStatus) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStore() == null || !order.getStore().getId().equals(storeId)) {
            throw new OrderNotFoundException(orderId);
        }
        return applyTransition(order, newStatus);
    }

    /**
     * Validate and apply a status transition on a loaded order and persist it.
     * Kept in one place so the transition rules are defined exactly once.
     * @param order the order to transition
     * @param newStatus the target status
     * @return the saved order
     * @throws OrderValidationException if the transition is not allowed
     */
    private Order applyTransition(Order order, OrderStatus newStatus) {
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
