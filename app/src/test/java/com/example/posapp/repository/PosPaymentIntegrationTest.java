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
import com.example.posapp.dto.PaymentRequest;
import com.example.posapp.dto.PosAuthenticateResponse;
import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.OrderNotFoundException;
import com.example.posapp.exception.PaymentNotFoundException;
import com.example.posapp.exception.PaymentValidationException;
import com.example.posapp.exception.PosSessionInvalidException;
import com.example.posapp.service.EmployeePinService;
import com.example.posapp.service.EmployeeStoreAssignmentService;
import com.example.posapp.service.PosAuthenticationService;
import com.example.posapp.service.PosOrderService;
import com.example.posapp.service.PosPaymentService;
import com.example.posapp.service.PosSessionService;

/**
 * End-to-end integration tests for the authenticated, store-scoped POS payment
 * lifecycle against a real PostgreSQL on the Flyway V1-V19 schema. These cover
 * what the isolated unit and web tests cannot: a payment persisting as a real
 * foreign key to an owned order, ownership being enforced through the
 * Payment -&gt; Order -&gt; Store path, one store being unable to pay for,
 * read, or transition another store's orders and payments, and missing and
 * foreign resources producing identical not-found responses.
 * <p>
 * Each test authenticates with its own PIN and fixture rows so the shared
 * process-local rate limiter never interferes, and an {@link AfterEach} wipes
 * every touched table (children before parents).
 * </p>
 */
@SpringBootTest
@Testcontainers
class PosPaymentIntegrationTest {

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
    private PosPaymentService posPaymentService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearFeatureTables() {
        // FK-safe order: children before the parents they reference.
        jdbcTemplate.execute("DELETE FROM payments");
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
    @DisplayName("POS create/get/status lifecycle persists a payment for an owned order")
    void fullLifecyclePersistsAndTransitions() {
        Fixture fx = fixture("paylc");
        Long orderId = createOrder(fx);

        Payment created = posPaymentService.createPayment(bearer(fx.token),
                new PaymentRequest(orderId, new BigDecimal("5.00"), PaymentMethod.CARD));
        assertThat(created.getId()).isNotNull();
        assertThat(created.getOrder().getId()).isEqualTo(orderId);
        assertThat(created.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(created.getAmount()).isEqualByComparingTo("5.00");

        // The order reference persisted as a real foreign key.
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT order_id FROM payments WHERE id = ?", created.getId());
        assertThat(row.get("order_id")).isEqualTo(orderId);

        // Same-store retrieval returns the payment.
        Payment fetched = posPaymentService.getPayment(bearer(fx.token), created.getId());
        assertThat(fetched.getId()).isEqualTo(created.getId());

        // Same-store status transitions follow the existing rules.
        Payment completed = posPaymentService.updateStatus(
                bearer(fx.token), created.getId(), PaymentStatus.COMPLETED);
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

        Payment refunded = posPaymentService.updateStatus(
                bearer(fx.token), created.getId(), PaymentStatus.REFUNDED);
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        // A terminal state cannot advance (existing rule preserved).
        assertThatThrownBy(() -> posPaymentService.updateStatus(
                bearer(fx.token), created.getId(), PaymentStatus.COMPLETED))
                .isInstanceOf(PaymentValidationException.class);
    }

    @Test
    @DisplayName("existing payment rules still apply: amount must be positive, cancelled orders rejected")
    void amountAndCancelledRulesPreserved() {
        Fixture fx = fixture("payrules");
        Long orderId = createOrder(fx);

        assertThatThrownBy(() -> posPaymentService.createPayment(bearer(fx.token),
                new PaymentRequest(orderId, BigDecimal.ZERO, PaymentMethod.CARD)))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Payment amount must be greater than zero");

        // Cancel the order, then attempt to pay it.
        posOrderService.updateStatus(bearer(fx.token), orderId, OrderStatus.CANCELLED);
        assertThatThrownBy(() -> posPaymentService.createPayment(bearer(fx.token),
                new PaymentRequest(orderId, new BigDecimal("5.00"), PaymentMethod.CARD)))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot create payment for a cancelled order");
    }

    @Test
    @DisplayName("the disallowed transitions remain rejected on owned payments")
    void invalidTransitionRejected() {
        Fixture fx = fixture("paytrans");
        Long orderId = createOrder(fx);
        Long paymentId = posPaymentService.createPayment(bearer(fx.token),
                new PaymentRequest(orderId, new BigDecimal("5.00"), PaymentMethod.CARD)).getId();

        assertThatThrownBy(() -> posPaymentService.updateStatus(
                bearer(fx.token), paymentId, PaymentStatus.REFUNDED))
                .isInstanceOf(PaymentValidationException.class)
                .hasMessageContaining("Cannot transition from PENDING to REFUNDED");
    }

    @Test
    @DisplayName("multiple payments per order remain permitted")
    void multiplePaymentsPerOrderAllowed() {
        Fixture fx = fixture("payout");
        Long orderId = createOrder(fx);

        Payment first = posPaymentService.createPayment(bearer(fx.token),
                new PaymentRequest(orderId, new BigDecimal("3.00"), PaymentMethod.CARD));
        Payment second = posPaymentService.createPayment(bearer(fx.token),
                new PaymentRequest(orderId, new BigDecimal("4.00"), PaymentMethod.CASH));

        assertThat(first.getId()).isNotNull();
        assertThat(second.getId()).isNotNull().isNotEqualTo(first.getId());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE order_id = ?", Integer.class, orderId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("missing, unknown, and revoked sessions are refused before any payment work")
    void invalidSessionsRejected() {
        Fixture fx = fixture("paybad");
        Long orderId = createOrder(fx);

        assertThatThrownBy(() -> posPaymentService.createPayment(null,
                new PaymentRequest(orderId, new BigDecimal("5.00"), PaymentMethod.CARD)))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> posPaymentService.getPayment("Bearer unknown-token", 1L))
                .isInstanceOf(PosSessionInvalidException.class);

        // Revoked session: logout then reuse.
        sessionService.logout(bearer(fx.token));
        assertThatThrownBy(() -> posPaymentService.getPayment(bearer(fx.token), 1L))
                .isInstanceOf(PosSessionInvalidException.class);
    }

    @Test
    @DisplayName("an expired session is refused")
    void expiredSessionRejected() {
        Fixture fx = fixture("payexp");
        Long orderId = createOrder(fx);
        jdbcTemplate.update("UPDATE pos_session SET expires_at = ? WHERE employee_id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)), fx.employee.getId());

        assertThatThrownBy(() -> posPaymentService.createPayment(bearer(fx.token),
                new PaymentRequest(orderId, new BigDecimal("5.00"), PaymentMethod.CARD)))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> posPaymentService.getPayment(bearer(fx.token), 1L))
                .isInstanceOf(PosSessionInvalidException.class);
    }

    @Test
    @DisplayName("cross-store and missing orders/payments all return identical not-found responses")
    void crossStoreAndMissingPaymentsAreInvisible() {
        Fixture a = fixture("payx");
        // A second store with its own employee/session in the same organization.
        Store otherStore = newStore(a.organization, "Uptown");
        Employee otherEmployee = newEmployee(a.organization, "Bob", "payx-bob");
        assignmentService.assignEmployeeToStore(otherEmployee.getId(), otherStore.getId());
        pinService.setPin(otherEmployee.getId(), "8899");
        String tokenB = authenticationService
                .authenticate(otherStore.getId(), "8899", "payx-uptown").token();

        // Store A creates an order and pays for it.
        Long orderIdA = createOrder(a);
        Long paymentIdA = posPaymentService.createPayment(bearer(a.token),
                new PaymentRequest(orderIdA, new BigDecimal("5.00"), PaymentMethod.CARD)).getId();

        // Store B cannot pay for A's order - identical response to a missing order.
        assertThatThrownBy(() -> posPaymentService.createPayment(bearer(tokenB),
                new PaymentRequest(orderIdA, new BigDecimal("5.00"), PaymentMethod.CARD)))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessage("Order not found: " + orderIdA);

        // Store B cannot read or transition A's payment - identical to a missing payment.
        assertThatThrownBy(() -> posPaymentService.getPayment(bearer(tokenB), paymentIdA))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found: " + paymentIdA);
        assertThatThrownBy(() -> posPaymentService.updateStatus(
                bearer(tokenB), paymentIdA, PaymentStatus.COMPLETED))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found: " + paymentIdA);

        // A nonexistent payment and a nonexistent order produce the same shape.
        assertThatThrownBy(() -> posPaymentService.getPayment(bearer(a.token), 999999L))
                .isInstanceOf(PaymentNotFoundException.class)
                .hasMessage("Payment not found: 999999");
        assertThatThrownBy(() -> posPaymentService.createPayment(bearer(a.token),
                new PaymentRequest(999999L, new BigDecimal("5.00"), PaymentMethod.CARD)))
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
                new Organization("POS-PAY " + tag + " Acme"));
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

    private Long createOrder(Fixture fx) {
        Order order = posOrderService.createOrder(bearer(fx.token),
                new OrderRequest(DiningOption.DINE_IN, List.of(
                        new OrderRequest.OrderLineRequest(fx.productId, 1))));
        return order.getId();
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
}
