package com.example.posapp.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.OrderRequest;
import com.example.posapp.dto.OrderResponse;
import com.example.posapp.dto.OrderStatusUpdateRequest;
import com.example.posapp.entity.Order;
import com.example.posapp.exception.OrderNotFoundException;
import com.example.posapp.service.OrderService;

/**
 * REST controller for creating and retrieving orders.
 * <p>
 * Provides two endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/orders} — create a new order (returns 201)</li>
 *   <li>{@code GET /api/v1/orders/{id}} — retrieve an existing order (returns 200)</li>
 * </ul>
 * </p>
 * <p>
 * The controller converts between DTOs and entities, delegating business logic
 * to {@link OrderService}. Entities never cross the HTTP boundary.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    /**
     * Constructor for OrderController.
     * @param orderService the service for order operations
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Create a new order.
     * <p>
     * Converts the request DTO to service-layer inputs, delegates to
     * {@link OrderService#createOrder}, and maps the result to a response DTO.
     * Returns HTTP 201 Created on success.
     * </p>
     *
     * @param request the order request containing dining option and line items
     * @return the created order as a response DTO
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        List<OrderService.OrderLineInput> lineInputs = request.lines().stream()
                .map(line -> new OrderService.OrderLineInput(line.productId(), line.quantity()))
                .toList();

        Order order = orderService.createOrder(request.diningOption(), lineInputs);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(order));
    }

    /**
     * Retrieve an order by ID.
     * <p>
     * Returns HTTP 200 with the order response DTO when found, or throws
     * {@link OrderNotFoundException} (mapped to 404 by the exception handler)
     * when the order does not exist.
     * </p>
     *
     * @param id the order ID
     * @return the order as a response DTO
     * @throws OrderNotFoundException if the order does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable Long id) {
        Order order = orderService.getOrderById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    /**
     * Transition an order's status.
     * <p>
     * Delegates to {@link OrderService#transitionStatus} which enforces the
     * allowed transition rules. Returns HTTP 200 with the updated order on
     * success. Invalid transitions throw {@link com.example.posapp.exception.OrderValidationException}
     * (mapped to 400). Missing orders throw {@link OrderNotFoundException}
     * (mapped to 404).
     * </p>
     *
     * @param id the order ID
     * @param request the status update request containing the target status
     * @return the updated order as a response DTO
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable Long id,
            @RequestBody OrderStatusUpdateRequest request) {
        Order order = orderService.transitionStatus(id, request.status());
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
