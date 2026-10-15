package com.example.posapp.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.posapp.dto.OrderRequest;
import com.example.posapp.dto.PosSessionResponse;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Store;
import com.example.posapp.repository.EmployeeRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Authenticated POS order operations layered on the bearer-session foundation.
 * <p>
 * Every method first resolves the presented {@code Authorization} header
 * through {@link PosSessionService#currentSession(String)} - so a missing,
 * malformed, unknown, expired, or revoked token is refused with the same
 * generic 401 as the session endpoints, with no token logic duplicated here -
 * and then derives the store and employee <em>exclusively</em> from that
 * session. The client cannot choose the attribution: the request body carries
 * only the dining option and line items, and the store/employee identities are
 * read from the resolved session before the order is persisted.
 * </p>
 * <p>
 * Order creation, pricing, retrieval, and status-transition rules are all
 * reused from {@link OrderService}; this class adds no new order logic, it
 * only binds the session to it. Store and employee are attached as lazy
 * references (by the session's IDs) so persisting the order writes the foreign
 * keys without loading the full rows, and the response mapping only ever reads
 * their IDs.
 * </p>
 */
@Service
public class PosOrderService {

    private final PosSessionService sessionService;
    private final OrderService orderService;
    private final StoreRepository storeRepository;
    private final EmployeeRepository employeeRepository;

    /**
     * Constructor for PosOrderService.
     * @param sessionService the bearer-session resolver reused for every call
     * @param orderService the existing order business logic
     * @param storeRepository used to attach the session's store by ID
     * @param employeeRepository used to attach the session's employee by ID
     */
    public PosOrderService(PosSessionService sessionService,
                           OrderService orderService,
                           StoreRepository storeRepository,
                           EmployeeRepository employeeRepository) {
        this.sessionService = sessionService;
        this.orderService = orderService;
        this.storeRepository = storeRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Create an order attributed to the session's store and employee.
     * @param authorizationHeader the raw {@code Authorization} header
     * @param request the order request (dining option and lines only)
     * @return the persisted, attributed order
     * @throws com.example.posapp.exception.PosSessionInvalidException when the
     *         session is missing, malformed, unknown, expired, or revoked
     * @throws com.example.posapp.exception.OrderValidationException on invalid
     *         dining option, empty lines, unknown/inactive product, or bad
     *         quantity (the standard order rules from {@link OrderService})
     */
    public Order createOrder(String authorizationHeader, OrderRequest request) {
        PosSessionResponse session = sessionService.currentSession(authorizationHeader);
        Store store = storeRepository.getReferenceById(session.storeId());
        Employee employee = employeeRepository.getReferenceById(session.employeeId());

        List<OrderService.OrderLineInput> lineInputs = request.lines().stream()
                .map(line -> new OrderService.OrderLineInput(line.productId(), line.quantity()))
                .toList();

        return orderService.createOrder(request.diningOption(), lineInputs, store, employee);
    }

    /**
     * Retrieve an order, but only if it belongs to the session's store.
     * @param authorizationHeader the raw {@code Authorization} header
     * @param orderId the order ID
     * @return the order, guaranteed to be owned by the session's store
     * @throws com.example.posapp.exception.PosSessionInvalidException when the
     *         session is missing, malformed, unknown, expired, or revoked
     * @throws com.example.posapp.exception.OrderNotFoundException when the order
     *         is missing or owned by another store (identical response in both
     *         cases)
     */
    public Order getOrder(String authorizationHeader, Long orderId) {
        PosSessionResponse session = sessionService.currentSession(authorizationHeader);
        return orderService.getOrder(orderId, session.storeId());
    }

    /**
     * Transition an order's status, but only if it belongs to the session's store.
     * @param authorizationHeader the raw {@code Authorization} header
     * @param orderId the order ID
     * @param newStatus the target status
     * @return the updated order
     * @throws com.example.posapp.exception.PosSessionInvalidException when the
     *         session is missing, malformed, unknown, expired, or revoked
     * @throws com.example.posapp.exception.OrderNotFoundException when the order
     *         is missing or owned by another store (identical response in both
     *         cases)
     * @throws com.example.posapp.exception.OrderValidationException on a
     *         disallowed transition (the standard transition rules)
     */
    public Order updateStatus(String authorizationHeader, Long orderId, OrderStatus newStatus) {
        PosSessionResponse session = sessionService.currentSession(authorizationHeader);
        return orderService.transitionStatus(orderId, session.storeId(), newStatus);
    }
}
