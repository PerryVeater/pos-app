package com.example.posapp.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderLine;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Product;
import com.example.posapp.repository.OrderRepository;
import com.example.posapp.repository.ProductRepository;

/**
 * Unit tests for the {@link OrderService} business rules.
 * <p>
 * The repositories are mocked, so these tests exercise the service layer in
 * isolation: price capture, quantity validation, product existence checks,
 * and delegation to the repositories. No Spring context or database is
 * required.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepo;

    @Mock
    private ProductRepository productRepo;

    @InjectMocks
    private OrderService orderService;

    /**
     * Build an active, uncategorized product from a decimal string so test
     * money values use the exact {@code BigDecimal} construction required
     * for monetary data.
     */
    private static Product product(String name, String sku, String price) {
        return new Product(name, sku, new BigDecimal(price), true);
    }

    // --- createOrder ---

    @Test
    @DisplayName("createOrder: builds order with PENDING status, captured prices, and saves")
    void createOrderBuildsOrderWithCapturedPrices() {
        Product cola = product("Cola", "COLA-001", "2.50");
        Product fries = product("Fries", "FRIES-001", "4.00");
        when(productRepo.findById(1L)).thenReturn(Optional.of(cola));
        when(productRepo.findById(2L)).thenReturn(Optional.of(fries));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(List.of(
                new OrderService.OrderLineInput(1L, 2),
                new OrderService.OrderLineInput(2L, 1)));

        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getLines()).hasSize(2);

        OrderLine colaLine = saved.getLines().stream()
                .filter(l -> l.getProduct() == cola)
                .findFirst().orElseThrow();
        assertThat(colaLine.getQuantity()).isEqualTo(2);
        assertThat(colaLine.getUnitPrice()).isEqualByComparingTo("2.50");
        assertThat(colaLine.getUnitPrice()).hasScaleOf(2);

        OrderLine friesLine = saved.getLines().stream()
                .filter(l -> l.getProduct() == fries)
                .findFirst().orElseThrow();
        assertThat(friesLine.getQuantity()).isEqualTo(1);
        assertThat(friesLine.getUnitPrice()).isEqualByComparingTo("4.00");

        verify(orderRepo).save(saved);
    }

    @Test
    @DisplayName("createOrder: unit price is a snapshot — later product price change does not affect the line")
    void createOrderCapturesPriceAtCreationTime() {
        Product cola = product("Cola", "COLA-001", "2.50");
        when(productRepo.findById(1L)).thenReturn(Optional.of(cola));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(List.of(
                new OrderService.OrderLineInput(1L, 1)));

        // Simulate a later price change on the product
        cola.setPrice(new BigDecimal("3.00"));

        // The order line's unit price is still the original snapshot
        assertThat(saved.getLines().get(0).getUnitPrice()).isEqualByComparingTo("2.50");
    }

    @Test
    @DisplayName("createOrder: each line's back-reference points to the owning order")
    void createOrderLinesBackReferenceOrder() {
        Product cola = product("Cola", "COLA-001", "2.50");
        when(productRepo.findById(1L)).thenReturn(Optional.of(cola));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(List.of(
                new OrderService.OrderLineInput(1L, 1)));

        assertThat(saved.getLines().get(0).getOrder()).isSameAs(saved);
    }

    @Test
    @DisplayName("createOrder: rejects nonexistent product and does not save")
    void createOrderRejectsNonexistentProduct() {
        when(productRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(List.of(
                new OrderService.OrderLineInput(99L, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Product not found");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects inactive product and does not save")
    void createOrderRejectsInactiveProduct() {
        Product inactive = new Product("Legacy Fries", "LEGACY-001", new BigDecimal("5.00"), false);
        when(productRepo.findById(1L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> orderService.createOrder(List.of(
                new OrderService.OrderLineInput(1L, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects inactive product even when other lines are valid (nothing saved)")
    void createOrderRejectsInactiveProductMixedLines() {
        Product active = product("Cola", "COLA-001", "2.50");
        Product inactive = new Product("Legacy Fries", "LEGACY-001", new BigDecimal("5.00"), false);
        when(productRepo.findById(1L)).thenReturn(Optional.of(active));
        when(productRepo.findById(2L)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> orderService.createOrder(List.of(
                new OrderService.OrderLineInput(1L, 1),
                new OrderService.OrderLineInput(2L, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not active");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects zero quantity and does not save")
    void createOrderRejectsZeroQuantity() {
        Product cola = product("Cola", "COLA-001", "2.50");
        when(productRepo.findById(1L)).thenReturn(Optional.of(cola));

        assertThatThrownBy(() -> orderService.createOrder(List.of(
                new OrderService.OrderLineInput(1L, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be positive");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects negative quantity and does not save")
    void createOrderRejectsNegativeQuantity() {
        Product cola = product("Cola", "COLA-001", "2.50");
        when(productRepo.findById(1L)).thenReturn(Optional.of(cola));

        assertThatThrownBy(() -> orderService.createOrder(List.of(
                new OrderService.OrderLineInput(1L, -1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Quantity must be positive");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects empty line list and does not save")
    void createOrderRejectsEmptyLineList() {
        assertThatThrownBy(() -> orderService.createOrder(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one line");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: rejects null line list and does not save")
    void createOrderRejectsNullLineList() {
        assertThatThrownBy(() -> orderService.createOrder(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one line");

        verify(orderRepo, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: zero unit price is accepted (free item)")
    void createOrderAcceptsZeroUnitPrice() {
        Product water = product("Tap water", "WATER-001", "0.00");
        when(productRepo.findById(1L)).thenReturn(Optional.of(water));
        when(orderRepo.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order saved = orderService.createOrder(List.of(
                new OrderService.OrderLineInput(1L, 1)));

        assertThat(saved.getLines().get(0).getUnitPrice()).isEqualByComparingTo("0.00");
    }

    // --- getOrderById ---

    @Test
    @DisplayName("getOrderById: returns the order when it exists")
    void getOrderByIdReturnsExistingOrder() {
        Order existing = new Order(OrderStatus.PENDING, java.time.LocalDateTime.now());
        when(orderRepo.findById(1L)).thenReturn(Optional.of(existing));

        assertThat(orderService.getOrderById(1L)).contains(existing);
    }

    @Test
    @DisplayName("getOrderById: returns empty when the order does not exist")
    void getOrderByIdReturnsEmptyForMissingOrder() {
        when(orderRepo.findById(99L)).thenReturn(Optional.empty());

        assertThat(orderService.getOrderById(99L)).isEmpty();
    }

    @Test
    @DisplayName("getOrderById: null id is rejected")
    void getOrderByIdRejectsNullId() {
        assertThatThrownBy(() -> orderService.getOrderById(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
