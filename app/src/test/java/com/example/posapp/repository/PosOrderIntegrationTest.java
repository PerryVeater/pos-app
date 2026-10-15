package com.example.posapp.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.posapp.dto.OrderRequest;
import com.example.posapp.dto.PosAuthenticateResponse;
import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.OrderNotFoundException;
import com.example.posapp.exception.OrderValidationException;
import com.example.posapp.exception.PosSessionInvalidException;
import com.example.posapp.service.EmployeePinService;
import com.example.posapp.service.EmployeeStoreAssignmentService;
import com.example.posapp.service.PosAuthenticationService;
import com.example.posapp.service.PosOrderService;
import com.example.posapp.service.PosSessionService;

/**
 * End-to-end integration tests for the authenticated, store-scoped POS order
 * lifecycle against a real PostgreSQL over the Flyway V18/V19 schema (Hibernate
 * {@code ddl-auto=validate} re-confirms the now-{@code NOT NULL}
 * {@code orders.store_id} and {@code orders.employee_id} columns on context
 * boot). These cover what the isolated unit and web tests cannot: attribution
 * actually persisting as foreign keys, sessions from one store being unable to
 * read or transition another store's orders, and cross-store and missing orders
 * producing the identical not-found.
 * <p>
 * Each test authenticates with its own PIN and fixture rows so the shared
 * process-local rate limiter never interferes, and an {@link AfterEach} wipes
 * every touched table.
 * </p>
 */
@SpringBootTest
@Testcontainers
class PosOrderIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private MenuItemRepository menuItemRepository;
    @Autowired
    private EmployeePinService pinService;
    @Autowired
    private EmployeeStoreAssignmentService assignmentService;
    @Autowired
    private PosAuthenticationService authenticationService;
    @Autowired
    private PosSessionService sessionService;
    @Autowired
    private PosOrderService posOrderService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearFeatureTables() {
        jdbcTemplate.execute("DELETE FROM order_line");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM pos_session");
        jdbcTemplate.execute("DELETE FROM employee_pin_store_scope");
        jdbcTemplate.execute("DELETE FROM employee_pin_credential");
        jdbcTemplate.execute("DELETE FROM employee_store_assignment");
        jdbcTemplate.execute("DELETE FROM employee");
        jdbcTemplate.execute("DELETE FROM stores");
        jdbcTemplate.execute("DELETE FROM organization");
        jdbcTemplate.execute("DELETE FROM product");
    }

    @Test
    @DisplayName("POS create/get/status lifecycle persists session-derived attribution")
    void fullLifecycleAttributesFromSession() {
        Fixture fx = fixture("poslc");
        OrderRequest request = new OrderRequest(DiningOption.DINE_IN, List.of(
                new OrderRequest.OrderLineRequest(fx.productId, 2)));

        Order created = posOrderService.createOrder(bearer(fx.token), request);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStore().getId()).isEqualTo(fx.store.getId());
        assertThat(created.getEmployee().getId()).isEqualTo(fx.employee.getId());
        // Existing pricing behavior is preserved: snapshot price x quantity.
        assertThat(created.getLines()).hasSize(1);
        assertThat(created.getTotal()).isEqualByComparingTo("5.00");

        // The attribution persisted as real foreign keys.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT store_id, employee_id FROM orders WHERE id = ?", created.getId());
        assertThat(row.get("store_id")).isEqualTo(fx.store.getId());
        assertThat(row.get("employee_id")).isEqualTo(fx.employee.getId());

        // Same-store retrieval returns the order with attribution.
        Order fetched = posOrderService.getOrder(bearer(fx.token), created.getId());
        assertThat(fetched.getId()).isEqualTo(created.getId());

        // Same-store status transition follows the existing rules.
        Order confirmed = posOrderService.updateStatus(
                bearer(fx.token), created.getId(), OrderStatus.CONFIRMED);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        // Invalid transitions remain rejected on owned orders.
        assertThatThrownBy(() -> posOrderService.updateStatus(
                bearer(fx.token), created.getId(), OrderStatus.PENDING))
                .isInstanceOf(OrderValidationException.class);
    }

    @Test
    @DisplayName("the client cannot choose attribution: a POS order always carries the session's store")
    void attributionIsDerivedNotSupplied() {
        Fixture fx = fixture("posattr");
        // The request body has no store or employee field at all.
        OrderRequest request = new OrderRequest(DiningOption.TAKEOUT, List.of(
                new OrderRequest.OrderLineRequest(fx.productId, 1)));

        Order created = posOrderService.createOrder(bearer(fx.token), request);
        assertThat(created.getStore().getId()).isEqualTo(fx.store.getId());
        assertThat(created.getEmployee().getId()).isEqualTo(fx.employee.getId());
    }

    @Test
    @DisplayName("missing, unknown, and revoked sessions are refused before any order work")
    void invalidSessionsRejected() {
        Fixture fx = fixture("posbad");

        assertThatThrownBy(() -> posOrderService.createOrder(null, orderRequest(fx)))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> posOrderService.getOrder("Bearer unknown-token", 1L))
                .isInstanceOf(PosSessionInvalidException.class);

        // Revoked session: logout then reuse.
        sessionService.logout(bearer(fx.token));
        assertThatThrownBy(() -> posOrderService.getOrder(bearer(fx.token), 1L))
                .isInstanceOf(PosSessionInvalidException.class);
    }

    @Test
    @DisplayName("an expired session is refused")
    void expiredSessionRejected() {
        Fixture fx = fixture("posexp");
        jdbcTemplate.update("UPDATE pos_session SET expires_at = ? WHERE employee_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), fx.employee.getId());

        assertThatThrownBy(() -> posOrderService.getOrder(bearer(fx.token), 1L))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> posOrderService.createOrder(bearer(fx.token), orderRequest(fx)))
                .isInstanceOf(PosSessionInvalidException.class);
    }

    @Test
    @DisplayName("cross-store and missing orders all return the identical not-found")
    void crossStoreAndMissingOrdersAreInvisible() {
        Fixture fx = fixture("posx");
        // A second store with its own employee/session in the same organization.
        Store otherStore = newStore(fx.organization, "Uptown");
        Employee otherEmployee = newEmployee(fx.organization, "Bob", "posx-bob");
        assignmentService.assignEmployeeToStore(otherEmployee.getId(), otherStore.getId());
        pinService.setPin(otherEmployee.getId(), "8899");
        String otherToken = authenticationService
                .authenticate(otherStore.getId(), "8899", "posx-uptown").token();

        // Store A creates an order.
        Long orderId = posOrderService.createOrder(bearer(fx.token), orderRequest(fx)).getId();

        // Store B cannot read or transition it - same exception/message as missing.
        String notFound = "Order not found: " + orderId;
        assertThatThrownBy(() -> posOrderService.getOrder(bearer(otherToken), orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage(notFound);
        assertThatThrownBy(() -> posOrderService.updateStatus(
                bearer(otherToken), orderId, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage(notFound);

        // A nonexistent order produces the identical response.
        assertThatThrownBy(() -> posOrderService.getOrder(bearer(fx.token), 999999L))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found: 999999");
    }

    // --- fixtures ---

    private record Fixture(Organization organization, Employee employee, Store store,
                           Long productId, String token) {
    }

    /**
     * One org, one store, one active employee assigned to it with PIN 0421,
     * one $2.50 product, and a fresh authenticated bearer session - all with
     * test-unique names so repeated runs never collide.
     */
    private Fixture fixture(String tag) {
        Organization org = organizationRepository.save(
                new Organization("POS-ORDER " + tag + " Acme"));
        Employee employee = newEmployee(org, "Ada", tag + "-ada");
        Store store = newStore(org, "Downtown");
        assignmentService.assignEmployeeToStore(employee.getId(), store.getId());
        pinService.setPin(employee.getId(), "0421");
        Long productId = menuItemRepository.save(
                new MenuItem("Cola " + tag, "COLA-" + tag, new BigDecimal("2.50"), true)).getId();
        PosAuthenticateResponse auth =
                authenticationService.authenticate(store.getId(), "0421", tag + "-client");
        return new Fixture(org, employee, store, productId, auth.token());
    }

    private Employee newEmployee(Organization owner, String name, String email) {
        Employee employee = new Employee(name);
        employee.setOrganization(owner);
        employee.setEmail(email + "@example.com");
        return employeeRepository.save(employee);
    }

    private Store newStore(Organization owner, String name) {
        Store store = new Store(null, name, "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
        store.setOrganization(owner);
        return storeRepository.save(store);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private OrderRequest orderRequest(Fixture fx) {
        return new OrderRequest(DiningOption.DINE_IN, List.of(
                new OrderRequest.OrderLineRequest(fx.productId, 1)));
    }
}
