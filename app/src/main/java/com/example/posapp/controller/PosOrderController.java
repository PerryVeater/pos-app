package com.example.posapp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.OrderRequest;
import com.example.posapp.dto.OrderResponse;
import com.example.posapp.dto.OrderStatusUpdateRequest;
import com.example.posapp.entity.Order;
import com.example.posapp.service.PosOrderService;

/**
 * Authenticated, store-scoped POS order endpoints.
 * <p>
 * All three require an {@code Authorization: Bearer <token>} header issued by
 * POS authentication. The store and employee an order is attributed to are
 * derived exclusively from that session - the request body never carries a
 * store or employee identity - and retrieval and status updates are confined
 * to orders owned by the session's store. A missing or malformed header is
 * passed through as {@code null} (so it is refused as an invalid session with
 * 401, never as a 400), and an order that is absent or owned by another store is
 * indistinguishable from a non-existent one (404).
 * </p>
 * <p>
 * This is the sole HTTP interface for orders. The legacy unauthenticated
 * {@code /api/v1/orders} endpoints have been removed; every order is created,
 * read, and transitioned through this session-bound API.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/pos/orders")
public class PosOrderController {

    private final PosOrderService posOrderService;

    /**
     * Constructor for PosOrderController.
     * @param posOrderService the session-bound order service
     */
    public PosOrderController(PosOrderService posOrderService) {
        this.posOrderService = posOrderService;
    }

    /**
     * Create an order attributed to the session's store and employee.
     * @param authorization the raw {@code Authorization} header, possibly absent
     * @param request the order request (dining option and lines only)
     * @return 201 with the created order
     */
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody OrderRequest request) {
        Order order = posOrderService.createOrder(authorization, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(order));
    }

    /**
     * Retrieve an order owned by the session's store.
     * @param authorization the raw {@code Authorization} header, possibly absent
     * @param id the order ID
     * @return 200 with the order, or 404 when it is missing/other-store/unattributed
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id) {
        Order order = posOrderService.getOrder(authorization, id);
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    /**
     * Transition the status of an order owned by the session's store.
     * @param authorization the raw {@code Authorization} header, possibly absent
     * @param id the order ID
     * @param request the status update request
     * @return 200 with the updated order, or 404 when it is
     *         missing/other-store/unattributed
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @RequestBody OrderStatusUpdateRequest request) {
        Order order = posOrderService.updateStatus(authorization, id, request.status());
        return ResponseEntity.ok(OrderResponse.from(order));
    }
}
