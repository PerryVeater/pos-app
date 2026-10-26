package com.example.posapp.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Unit tests for the {@link OrderService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service layer in
 * isolation: attribution requirement, price capture, quantity validation,
 * product existence checks, store-scoped retrieval, and the status-transition
 * graph. No Spring context or database is required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepo;

    @Mock
    private MenuItemRepository menuItemRepo;

    @InjectMocks
    private OrderService orderService;

    /**
     * Build an active, uncategorized product from a decimal string so test
     * money values use the exact {@code BigDecimal} construction required
     * for monetary data.
     */
    private static MenuItem product(String name, String sku, String price) {
        return new MenuItem(name, sku, new BigDecimal(price), true);
    }

    /** An unsaved store reference for attribution; never queried for its ID here. */
    private static Store storeRef() {
        return mock(Store.class);
    }

    /** An unsaved employee reference for attribution; never queried for its ID here. */
    private static Employee employeeRef() {
        return mock(Employee.class);
    }

    /**
     * Build an attributed order (store id 2) in the given status, matching what
     * the store-scoped read/transition paths expect to find via the repository.
     */
    private static Order ownedOrder(OrderStatus status) {
        Store store = mock(Store.class);
        when(store.getId()).thenReturn(2L);
        Order order = new Order(status, DiningOption.DINE_IN, Instant.now());
        order.setStore(store);
        return order;
    }

    // --- createOrder: build & pricing ---

    @Test
    @DisplayName("createOrder: builds an attributed order with PENDING status, captured prices, and saves")
    void createOrderBuildsOrderWithCapturedPrices() {
        MenuItem cola = product("Cola", "COLA-001", "2.50");
        MenuItem fries = product("Fries", "FRIES-001", "4.00");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(cola));
        when(menuItemRepo.findById(2L)).thenReturn(Optional.of(fries));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(1L, 2),
                new OrderService.OrderLineInput(2L, 1)), storeRef(), employeeRef());

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getDiningOption()).isEqualTo(DiningOption.DINE_IN);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getLines()).hasSize(2);

        OrderLine colaLine = saved.getLines().stream()
                .filter(l -> l.getMenuItem() == cola)
                .findFirst().orElseThrow();
        assertThat(colaLine.getQuantity()).isEqualTo(2);
        assertThat(colaLine.getUnitPrice()).isEqualByComparingTo("2.50");
        assertThat(colaLine.getUnitPrice()).hasScaleOf(2);

        OrderLine friesLine = saved.getLines().stream()
                .filter(l -> l.getMenuItem() == fries)
                .findFirst().orElseThrow();
        assertThat(friesLine.getQuantity()).isEqualTo(1);
        assertThat(friesLine.getUnitPrice()).isEqualByComparingTo("4.00");

        verify(orderRepo).save(saved);
    }

    @Test
    @DisplayName("createOrder: stamps the supplied store and employee attribution on the order")
    void createOrderStampsAttribution() {
        MenuItem cola = product("Cola", "COLA-001", "2.50");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(cola));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        Store store = storeRef();
        Employee employee = employeeRef();

        Order saved = orderService.createOrder(DiningOption.DINE_IN,
                List.of(new OrderService.OrderLineInput(1L, 1)), store, employee);

        assertThat(saved.getStore()).isSameAs(store);
        assertThat(saved.getEmployee()).isSameAs(employee);
    }

    @Test
    @DisplayName("createOrder: unit price is a snapshot — later product price change does not affect the line")
    void createOrderCapturesPriceAtCreationTime() {
        MenuItem cola = product("Cola", "COLA-001", "2.50");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(cola));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(1L, 1)), storeRef(), employeeRef());

        // Simulate a later price change on the menuItem
        cola.setPrice(new BigDecimal("3.00"));

        // The order line's unit price is still the original snapshot
        assertThat(saved.getLines().get(0).getUnitPrice()).isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("createOrder: each line's back-reference points to the owning order")
    void createOrderLinesBackReferenceOrder() {
        MenuItem cola = product("Cola", "COLA-001", "2.50");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(cola));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(1L, 1)), storeRef(), employeeRef());

        assertThat(saved.getLines().get(0).getOrder()).isSameAs(saved);
    }

    @Test
    @DisplayName("createOrder: zero unit price is accepted (free item)")
    void createOrderAcceptsZeroUnitPrice() {
        MenuItem water = product("Tap water", "WATER-001", "0.00");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(water));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(1L, 1)), storeRef(), employeeRef());

        assertThat(saved.getLines().get(0).getUnitPrice()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("createOrder: accepts TAKEOUT dining option")
    void createOrderAcceptsTakeoutDiningOption() {
        MenuItem cola = product("Cola", "COLA-001", "2.50");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(cola));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(DiningOption.TAKEOUT, List.of(
                new OrderService.OrderLineInput(1L, 1)), storeRef(), employeeRef());

        assertThat(saved.getDiningOption()).isEqualTo(DiningOption.TAKEOUT);
    }

    @Test
    @DisplayName("createOrder: accepts ONLINE dining option")
    void createOrderAcceptsOnlineDiningOption() {
        MenuItem cola = product("Cola", "COLA-001", "2.50");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(cola));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(DiningOption.ONLINE, List.of(
                new OrderService.OrderLineInput(1L, 1)), storeRef(), employeeRef());

        assertThat(saved.getDiningOption()).isEqualTo(DiningOption.ONLINE);
    }

    // --- createOrder: validation ---

    @Test
    @DisplayName("createOrder: rejects a null store attribution and does not save")
    void createOrderRejectsNullStore() {
        assertThatThrownBy(() -> orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(1L, 1)), null, employeeRef()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("attribution is required");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects a null employee attribution and does not save")
    void createOrderRejectsNullEmployee() {
        assertThatThrownBy(() -> orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(1L, 1)), storeRef(), null))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("attribution is required");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects null dining option and does not save")
    void createOrderRejectsNullDiningOption() {
        assertThatThrownBy(() -> orderService.createOrder(null, List.of(
                new OrderService.OrderLineInput(1L, 1)), storeRef(), employeeRef()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Dining option is required");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects empty line list and does not save")
    void createOrderRejectsEmptyLineList() {
        assertThatThrownBy(() -> orderService.createOrder(DiningOption.DINE_IN, List.of(),
                storeRef(), employeeRef()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("at least one line");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects null line list and does not save")
    void createOrderRejectsNullLineList() {
        assertThatThrownBy(() -> orderService.createOrder(DiningOption.DINE_IN, null,
                storeRef(), employeeRef()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("at least one line");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects nonexistent product and does not save")
    void createOrderRejectsNonexistentMenuItem() {
        when(menuItemRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(99L, 1)), storeRef(), employeeRef()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Product not found");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects inactive product and does not save")
    void createOrderRejectsInactiveMenuItem() {
        MenuItem inactive = new MenuItem("Legacy Fries", "LEGACY-001", new BigDecimal("5.00"), false);
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(1L, 1)), storeRef(), employeeRef()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("not active");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects inactive product even when other lines are valid (nothing saved)")
    void createOrderRejectsInactiveMenuItemMixedLines() {
        MenuItem active = product("Cola", "COLA-001", "2.50");
        MenuItem inactive = new MenuItem("Legacy Fries", "LEGACY-001", new BigDecimal("5.00"), false);
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(active));
        when(menuItemRepo.findById(2L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(1L, 1),
                new OrderService.OrderLineInput(2L, 1)), storeRef(), employeeRef()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("not active");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects zero quantity and does not save")
    void createOrderRejectsZeroQuantity() {
        MenuItem cola = product("Cola", "COLA-001", "2.50");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(cola));

        assertThatThrownBy(() -> orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(1L, 0)), storeRef(), employeeRef()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Quantity must be positive");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects negative quantity and does not save")
    void createOrderRejectsNegativeQuantity() {
        MenuItem cola = product("Cola", "COLA-001", "2.50");
        when(menuItemRepo.findById(1L)).thenReturn(Optional.of(cola));

        assertThatThrownBy(() -> orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(1L, -1)), storeRef(), employeeRef()))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Quantity must be positive");

        verify(orderRepo, never()).save(any(Order.class));
    }

    // --- getOrder (store-scoped) ---

    @Test
    @DisplayName("getOrder: returns the order when it belongs to the session store")
    void getOrderSameStoreReturns() {
        Order order = ownedOrder(OrderStatus.PENDING);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));

        assertThat(orderService.getOrder(1L, 2L)).isSameAs(order);
    }

    @Test
    @DisplayName("getOrder: a nonexistent order is not found")
    void getOrderMissingThrowsNotFound() {
        when(orderRepo.findWithLinesById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(99L, 2L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found: 99");
    }

    @Test
    @DisplayName("getOrder: a cross-store order is indistinguishable from missing")
    void getOrderCrossStoreThrowsNotFound() {
        Order order = ownedOrder(OrderStatus.PENDING);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrder(1L, 999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found: 1");
    }

    @Test
    @DisplayName("getOrder: null id is rejected")
    void getOrderRejectsNullId() {
        assertThatThrownBy(() -> orderService.getOrder(null, 2L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- transitionStatus (store-scoped): allowed transitions ---

    @Test
    @DisplayName("transitionStatus: PENDING → CONFIRMED is allowed")
    void transitionStatusPendingToConfirmed() {
        Order order = ownedOrder(OrderStatus.PENDING);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.transitionStatus(1L, 2L, OrderStatus.CONFIRMED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepo).save(order);
    }

    @Test
    @DisplayName("transitionStatus: PENDING → CANCELLED is allowed")
    void transitionStatusPendingToCancelled() {
        Order order = ownedOrder(OrderStatus.PENDING);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.transitionStatus(1L, 2L, OrderStatus.CANCELLED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepo).save(order);
    }

    @Test
    @DisplayName("transitionStatus: CONFIRMED → COMPLETED is allowed")
    void transitionStatusConfirmedToCompleted() {
        Order order = ownedOrder(OrderStatus.CONFIRMED);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.transitionStatus(1L, 2L, OrderStatus.COMPLETED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(orderRepo).save(order);
    }

    @Test
    @DisplayName("transitionStatus: CONFIRMED → CANCELLED is allowed")
    void transitionStatusConfirmedToCancelled() {
        Order order = ownedOrder(OrderStatus.CONFIRMED);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.transitionStatus(1L, 2L, OrderStatus.CANCELLED);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepo).save(order);
    }

    // --- transitionStatus (store-scoped): rejected transitions ---

    @Test
    @DisplayName("transitionStatus: PENDING → COMPLETED is rejected")
    void transitionStatusPendingToCompletedRejected() {
        Order order = ownedOrder(OrderStatus.PENDING);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.transitionStatus(1L, 2L, OrderStatus.COMPLETED))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Cannot transition from PENDING to COMPLETED");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("transitionStatus: PENDING → PENDING is rejected")
    void transitionStatusPendingToPendingRejected() {
        Order order = ownedOrder(OrderStatus.PENDING);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.transitionStatus(1L, 2L, OrderStatus.PENDING))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Cannot transition from PENDING to PENDING");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("transitionStatus: CONFIRMED → PENDING is rejected")
    void transitionStatusConfirmedToPendingRejected() {
        Order order = ownedOrder(OrderStatus.CONFIRMED);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.transitionStatus(1L, 2L, OrderStatus.PENDING))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Cannot transition from CONFIRMED to PENDING");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("transitionStatus: COMPLETED → anything is rejected")
    void transitionStatusCompletedToAnythingRejected() {
        Order order = ownedOrder(OrderStatus.COMPLETED);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.transitionStatus(1L, 2L, OrderStatus.CANCELLED))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Cannot transition from COMPLETED to CANCELLED");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("transitionStatus: CANCELLED → anything is rejected")
    void transitionStatusCancelledToAnythingRejected() {
        Order order = ownedOrder(OrderStatus.CANCELLED);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.transitionStatus(1L, 2L, OrderStatus.PENDING))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("Cannot transition from CANCELLED to PENDING");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("transitionStatus: nonexistent order throws OrderNotFoundException")
    void transitionStatusNonexistentOrderThrowsNotFound() {
        when(orderRepo.findWithLinesById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.transitionStatus(99L, 2L, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found: 99");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("transitionStatus: cross-store order is not found and not saved")
    void transitionStatusCrossStoreThrowsNotFound() {
        Order order = ownedOrder(OrderStatus.PENDING);
        when(orderRepo.findWithLinesById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.transitionStatus(1L, 999L, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining("Order not found: 1");

        verify(orderRepo, never()).save(any(Order.class));
    }
}
