package com.example.posapp.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderLine;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.exception.OrderValidationException;
import com.example.posapp.service.OrderService;

/**
 * Integration tests for the Order persistence stack against a real PostgreSQL.
 * <p>
 * Each test run boots the full application context against a disposable
 * PostgreSQL 16 container managed by Testcontainers: Flyway applies the
 * migrations (V1–V4) first, then Hibernate {@code ddl-auto=validate} verifies
 * the schema before the context starts. The container gets a random port, so
 * it never collides with the Docker Compose development database.
 * </p>
 * <p>
 * The {@link com.example.posapp.loader.DataLoader} seed runs once during
 * context startup, so tests assert only on rows they create themselves and
 * on schema metadata, never on global row counts.
 * </p>
 */
@SpringBootTest
@Testcontainers
class OrderRepositoryIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds its
     * JDBC URL and credentials into the Spring environment in place of the
     * configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MenuItemRepository productRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("context boots: Flyway records V1–V9 as successful and Hibernate validate passed")
    void contextBootsWithMigratedSchema() {
        // Reaching this point proves the context started, which means Flyway
        // migrated first and ddl-auto=validate accepted the schema. Verify the
        // recorded history as well:
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history).hasSize(9);
        assertThat(history.get(0))
                .containsEntry("version", "1")
                .containsEntry("description", "create product table")
                .containsEntry("success", true);
        assertThat(history.get(1))
                .containsEntry("version", "2")
                .containsEntry("description", "add product sku and active")
                .containsEntry("success", true);
        assertThat(history.get(2))
                .containsEntry("version", "3")
                .containsEntry("description", "add product category")
                .containsEntry("success", true);
        assertThat(history.get(3))
                .containsEntry("version", "4")
                .containsEntry("description", "add orders and order lines")
                .containsEntry("success", true);
        assertThat(history.get(4))
                .containsEntry("version", "5")
                .containsEntry("description", "create payment table")
                .containsEntry("success", true);
        assertThat(history.get(5))
                .containsEntry("version", "6")
                .containsEntry("description", "add order dining option")
                .containsEntry("success", true);
        assertThat(history.get(6))
                .containsEntry("version", "7")
                .containsEntry("description", "add menu and menu groups")
                .containsEntry("success", true);
        assertThat(history.get(7))
                .containsEntry("version", "8")
                .containsEntry("description", "add menu item assignments")
                .containsEntry("success", true);
        assertThat(history.get(8))
                .containsEntry("version", "9")
                .containsEntry("description", "add modifier groups and modifiers")
                .containsEntry("success", true);
    }

    @Test
    @DisplayName("orders table matches the JPA model: id, status varchar(16), dining_option varchar(20), created_at timestamp")
    void ordersTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable, character_maximum_length"
                + " FROM information_schema.columns WHERE table_name = 'orders'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "status", "created_at", "dining_option");
        assertThat(columns.get(0))
                .containsEntry("column_name", "id")
                .containsEntry("data_type", "bigint");
        assertThat(columns.get(1))
                .containsEntry("column_name", "status")
                .containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 16)
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(2))
                .containsEntry("column_name", "created_at")
                .containsEntry("data_type", "timestamp without time zone")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(3))
                .containsEntry("column_name", "dining_option")
                .containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 20)
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("order_line table matches the JPA model: order_id, product_id, quantity, unit_price numeric(10,2)")
    void orderLineTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable, numeric_precision, numeric_scale"
                + " FROM information_schema.columns WHERE table_name = 'order_line'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "order_id", "product_id", "quantity", "unit_price");
        assertThat(columns.get(1))
                .containsEntry("column_name", "order_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(2))
                .containsEntry("column_name", "product_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(3))
                .containsEntry("column_name", "quantity")
                .containsEntry("data_type", "integer")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(4))
                .containsEntry("column_name", "unit_price")
                .containsEntry("data_type", "numeric")
                .containsEntry("numeric_precision", 10)
                .containsEntry("numeric_scale", 2)
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("order_line has two FKs: order_id→orders(id) and product_id→product(id), both enforced")
    void orderLineForeignKeysAreEnforced() {
        // Catalog: exactly two FKs on order_line, ordered by column name.
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList(
                "SELECT tc.constraint_name, kcu.column_name, ccu.table_name AS referenced_table,"
                + " ccu.column_name AS referenced_column"
                + " FROM information_schema.table_constraints tc"
                + " JOIN information_schema.key_column_usage kcu"
                + "   ON tc.constraint_name = kcu.constraint_name"
                + "  AND tc.constraint_schema = kcu.constraint_schema"
                + " JOIN information_schema.constraint_column_usage ccu"
                + "   ON tc.constraint_name = ccu.constraint_name"
                + "  AND tc.constraint_schema = ccu.constraint_schema"
                + " WHERE tc.table_schema = 'public' AND tc.table_name = 'order_line'"
                + "   AND tc.constraint_type = 'FOREIGN KEY'"
                + " ORDER BY kcu.column_name");

        assertThat(foreignKeys).hasSize(2);
        assertThat(foreignKeys.get(0))
                .containsEntry("constraint_name", "fk_order_line_order")
                .containsEntry("column_name", "order_id")
                .containsEntry("referenced_table", "orders")
                .containsEntry("referenced_column", "id");
        assertThat(foreignKeys.get(1))
                .containsEntry("constraint_name", "fk_order_line_product")
                .containsEntry("column_name", "product_id")
                .containsEntry("referenced_table", "product")
                .containsEntry("referenced_column", "id");

        // Behavior: a line cannot reference a nonexistent order.
        MenuItem p = productRepository.save(
                new MenuItem("FK Cola", "FK-ORD-001", new BigDecimal("1.00"), true));
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO order_line (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                999999L, p.getId(), 1, new BigDecimal("1.00")))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Behavior: a line cannot reference a nonexistent product.
        Order order = new Order(OrderStatus.PENDING, DiningOption.DINE_IN, LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO order_line (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, ?)",
                savedOrder.getId(), 999999L, 1, new BigDecimal("1.00")))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup
        orderRepository.deleteById(savedOrder.getId());
        productRepository.deleteById(p.getId());
    }

    @Test
    @DisplayName("cannot delete a product referenced by an order line (FK protect)")
    void cannotDeleteMenuItemInUseByOrderLine() {
        MenuItem p = productRepository.save(
                new MenuItem("FK Cola 2", "FK-ORD-002", new BigDecimal("1.00"), true));
        Order order = new Order(OrderStatus.PENDING, DiningOption.DINE_IN, LocalDateTime.now());
        order.addLine(new OrderLine(p, 1, new BigDecimal("1.00")));
        orderRepository.save(order);

        assertThatThrownBy(() -> productRepository.deleteById(p.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: remove the order first, then the product deletes fine.
        orderRepository.deleteById(order.getId());
        productRepository.deleteById(p.getId());
        assertThat(productRepository.findById(p.getId())).isEmpty();
    }

    @Test
    @DisplayName("cascade: deleting an order removes its lines")
    void cascadeDeleteRemovesOrderLines() {
        MenuItem cola = productRepository.save(
                new MenuItem("Cascade Cola", "INT-ORD-001", new BigDecimal("2.50"), true));

        Order order = new Order(OrderStatus.PENDING, DiningOption.DINE_IN, LocalDateTime.now());
        order.addLine(new OrderLine(cola, 2, new BigDecimal("2.50")));
        Order saved = orderRepository.save(order);
        Long orderId = saved.getId();

        // Verify the line exists
        assertThat(jdbcTemplate.queryForList("SELECT * FROM order_line WHERE order_id = ?", orderId))
                .hasSize(1);

        // Delete the order — cascade should remove the line
        orderRepository.deleteById(orderId);

        assertThat(orderRepository.findById(orderId)).isEmpty();
        assertThat(jdbcTemplate.queryForList("SELECT * FROM order_line WHERE order_id = ?", orderId))
                .isEmpty();

        productRepository.deleteById(cola.getId());
    }

    @Test
    @DisplayName("repository performs full CRUD against PostgreSQL, persisting lines and captured prices")
    void repositoryCrudAgainstPostgres() {
        // Setup: create products
        MenuItem cola = productRepository.save(
                new MenuItem("Integration Cola", "INT-ORD-003", new BigDecimal("2.50"), true));
        MenuItem fries = productRepository.save(
                new MenuItem("Integration Fries", "INT-ORD-004", new BigDecimal("4.00"), true));

        // Create: order with two lines
        Order order = new Order(OrderStatus.PENDING, DiningOption.DINE_IN, LocalDateTime.now());
        order.addLine(new OrderLine(cola, 2, new BigDecimal("2.50")));
        order.addLine(new OrderLine(fries, 1, new BigDecimal("4.00")));
        Order saved = orderRepository.save(order);
        assertThat(saved.getId()).isNotNull();

        // Read: identity, status, lines, and captured prices survive the round-trip
        Optional<Order> loaded = orderRepository.findById(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(loaded.get().getDiningOption()).isEqualTo(DiningOption.DINE_IN);
        assertThat(loaded.get().getCreatedAt()).isNotNull();
        assertThat(loaded.get().getLines()).hasSize(2);

        OrderLine colaLine = loaded.get().getLines().stream()
                .filter(l -> l.getMenuItem().getId().equals(cola.getId()))
                .findFirst().orElseThrow();
        assertThat(colaLine.getQuantity()).isEqualTo(2);
        assertThat(colaLine.getUnitPrice()).isEqualByComparingTo("2.50");
        assertThat(colaLine.getUnitPrice()).hasScaleOf(2);
        assertThat(colaLine.getMenuItem().getName()).isEqualTo("Integration Cola");

        OrderLine friesLine = loaded.get().getLines().stream()
                .filter(l -> l.getMenuItem().getId().equals(fries.getId()))
                .findFirst().orElseThrow();
        assertThat(friesLine.getQuantity()).isEqualTo(1);
        assertThat(friesLine.getUnitPrice()).isEqualByComparingTo("4.00");

        // Update: change status
        loaded.get().setStatus(OrderStatus.CONFIRMED);
        Order updated = orderRepository.save(loaded.get());
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Re-read: status persists
        assertThat(orderRepository.findById(saved.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);

        // Delete: cascade removes lines
        orderRepository.deleteById(saved.getId());
        assertThat(orderRepository.findById(saved.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForList("SELECT * FROM order_line WHERE order_id = ?", saved.getId()))
                .isEmpty();

        // Cleanup
        productRepository.deleteById(cola.getId());
        productRepository.deleteById(fries.getId());
    }

    @Test
    @DisplayName("OrderService: rejects inactive product against real PostgreSQL")
    void orderServiceRejectsInactiveMenuItem() {
        MenuItem inactive = productRepository.save(
                new MenuItem("Inactive Cola", "INT-ORD-INACTIVE", new BigDecimal("2.50"), false));

        assertThatThrownBy(() -> orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(inactive.getId(), 1))))
                .isInstanceOf(OrderValidationException.class)
                .hasMessageContaining("not active");

        // No order should have been created
        assertThat(jdbcTemplate.queryForList("SELECT * FROM orders")).isEmpty();

        productRepository.deleteById(inactive.getId());
    }

    @Test
    @DisplayName("OrderService: captures current product price as unitPrice against real PostgreSQL")
    void orderServiceCapturesCurrentPrice() {
        MenuItem cola = productRepository.save(
                new MenuItem("Price Cola", "INT-ORD-PRICE", new BigDecimal("3.50"), true));

        Order order = orderService.createOrder(DiningOption.DINE_IN, List.of(
                new OrderService.OrderLineInput(cola.getId(), 2)));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getDiningOption()).isEqualTo(DiningOption.DINE_IN);
        assertThat(order.getLines()).hasSize(1);
        assertThat(order.getLines().get(0).getUnitPrice()).isEqualByComparingTo("3.50");
        assertThat(order.getLines().get(0).getUnitPrice()).hasScaleOf(2);
        assertThat(order.getLines().get(0).getQuantity()).isEqualTo(2);

        // Cleanup
        orderRepository.deleteById(order.getId());
        productRepository.deleteById(cola.getId());
    }

    @Test
    @DisplayName("Order.getTotal: computed total survives save and reload against real PostgreSQL")
    void orderTotalSurvivesSaveAndReload() {
        MenuItem cola = productRepository.save(
                new MenuItem("Total Cola", "INT-ORD-TOTAL-1", new BigDecimal("2.50"), true));
        MenuItem fries = productRepository.save(
                new MenuItem("Total Fries", "INT-ORD-TOTAL-2", new BigDecimal("4.00"), true));

        // Create order with two lines: 3×2.50=7.50 + 2×4.00=8.00 = 15.50
        Order order = new Order(OrderStatus.PENDING, DiningOption.DINE_IN, LocalDateTime.now());
        order.addLine(new OrderLine(cola, 3, new BigDecimal("2.50")));
        order.addLine(new OrderLine(fries, 2, new BigDecimal("4.00")));
        Order saved = orderRepository.save(order);

        // Verify total before reload
        assertThat(saved.getTotal()).isEqualByComparingTo("15.50");
        assertThat(saved.getTotal()).hasScaleOf(2);

        // Reload from database and verify total is still correct
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTotal()).isEqualByComparingTo("15.50");
        assertThat(reloaded.getLines()).hasSize(2);

        // Cleanup
        orderRepository.deleteById(saved.getId());
        productRepository.deleteById(cola.getId());
        productRepository.deleteById(fries.getId());
    }

    @Test
    @DisplayName("OrderService.transitionStatus: PENDING → CONFIRMED is persisted and reloaded")
    void transitionStatusIsPersistedAndReloaded() {
        MenuItem cola = productRepository.save(
                new MenuItem("Transition Cola", "INT-ORD-TRANS-1", new BigDecimal("2.50"), true));

        // Create a PENDING order
        Order order = new Order(OrderStatus.PENDING, DiningOption.DINE_IN, LocalDateTime.now());
        order.addLine(new OrderLine(cola, 1, new BigDecimal("2.50")));
        Order saved = orderRepository.save(order);
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Transition to CONFIRMED via the service
        Order transitioned = orderService.transitionStatus(saved.getId(), OrderStatus.CONFIRMED);
        assertThat(transitioned.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Reload from database and verify the status persisted
        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Cleanup
        orderRepository.deleteById(saved.getId());
        productRepository.deleteById(cola.getId());
    }

    @Test
    @DisplayName("dining_option: persists TAKEOUT and ONLINE values correctly")
    void diningOptionPersistsAllValues() {
        MenuItem cola = productRepository.save(
                new MenuItem("Dining Cola", "INT-ORD-DINE-1", new BigDecimal("2.50"), true));

        // Test TAKEOUT
        Order takeoutOrder = new Order(OrderStatus.PENDING, DiningOption.TAKEOUT, LocalDateTime.now());
        takeoutOrder.addLine(new OrderLine(cola, 1, new BigDecimal("2.50")));
        Order savedTakeout = orderRepository.save(takeoutOrder);

        Order reloadedTakeout = orderRepository.findById(savedTakeout.getId()).orElseThrow();
        assertThat(reloadedTakeout.getDiningOption()).isEqualTo(DiningOption.TAKEOUT);

        // Test ONLINE
        Order onlineOrder = new Order(OrderStatus.PENDING, DiningOption.ONLINE, LocalDateTime.now());
        onlineOrder.addLine(new OrderLine(cola, 1, new BigDecimal("2.50")));
        Order savedOnline = orderRepository.save(onlineOrder);

        Order reloadedOnline = orderRepository.findById(savedOnline.getId()).orElseThrow();
        assertThat(reloadedOnline.getDiningOption()).isEqualTo(DiningOption.ONLINE);

        // Cleanup
        orderRepository.deleteById(savedTakeout.getId());
        orderRepository.deleteById(savedOnline.getId());
        productRepository.deleteById(cola.getId());
    }

    @Test
    @DisplayName("OrderService: creates order with dining option against real PostgreSQL")
    void orderServiceCreatesOrderWithDiningOption() {
        MenuItem cola = productRepository.save(
                new MenuItem("Dining Option Cola", "INT-ORD-DINE-2", new BigDecimal("2.50"), true));

        Order order = orderService.createOrder(DiningOption.ONLINE, List.of(
                new OrderService.OrderLineInput(cola.getId(), 1)));

        assertThat(order.getId()).isNotNull();
        assertThat(order.getDiningOption()).isEqualTo(DiningOption.ONLINE);

        // Reload and verify it persisted
        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getDiningOption()).isEqualTo(DiningOption.ONLINE);

        // Cleanup
        orderRepository.deleteById(order.getId());
        productRepository.deleteById(cola.getId());
    }
}
