package com.example.posapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.posapp.entity.DiningOption;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.MenuItem;
import com.example.posapp.entity.Order;
import com.example.posapp.entity.OrderLine;
import com.example.posapp.entity.OrderStatus;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Payment;
import com.example.posapp.entity.PaymentMethod;
import com.example.posapp.entity.PaymentStatus;
import com.example.posapp.entity.Store;

/**
 * Integration tests for the optimistic locking added by Flyway V23 (defect D5).
 * <p>
 * A lost update is a timing bug, so the test has to create the timing rather
 * than hope for it: two transactions each load the same row, both wait until
 * the other has read, and only then does one write while the other writes
 * after that commit. That is deterministic - the second transaction's read is
 * always superseded - so the assertion is about behaviour, not luck.
 * </p>
 * <p>
 * What is asserted is the contract the application depends on: the second
 * writer fails with an {@link OptimisticLockingFailureException} (which
 * {@code ApiExceptionHandler} turns into 409), the winner's value is the one
 * that survives, and the version advanced exactly once per successful write.
 * </p>
 */
@SpringBootTest
@Testcontainers
class OptimisticLockingIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds its
     * JDBC URL and credentials into the Spring environment in place of the
     * configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** How long a racer waits for its partner before the test fails. */
    private static final long RACE_TIMEOUT_SECONDS = 10;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Store store;
    private Employee employee;
    private MenuItem menuItem;

    /**
     * Persists the attribution and catalog rows every order needs, so each test
     * writes only the rows it is about.
     */
    @BeforeEach
    void createFixture() {
        Organization org = organizationRepository.save(new Organization("Optimistic IT Acme"));
        Store s = new Store(null, "Downtown", "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
        s.setOrganization(org);
        store = storeRepository.save(s);
        Employee e = new Employee("Ada");
        e.setOrganization(org);
        e.setEmail("ada-optimistic@example.com");
        employee = employeeRepository.save(e);
        menuItem = menuItemRepository.save(
                new MenuItem("Cola", "OPTIMISTIC-COLA-001", new BigDecimal("2.50"), true));
    }

    /**
     * Removes everything this class wrote, children before parents, so the
     * container is left as the next class expects to find it.
     */
    @AfterEach
    void clearAllTables() {
        jdbcTemplate.execute("DELETE FROM payments");
        jdbcTemplate.execute("DELETE FROM order_line");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM employee");
        jdbcTemplate.execute("DELETE FROM stores");
        jdbcTemplate.execute("DELETE FROM organization");
    }

    @Test
    @DisplayName("a concurrent order transition fails instead of overwriting the winner, and the winner survives")
    void concurrentOrderTransitionsConflict() throws InterruptedException {
        Long orderId = persistedOrder().getId();

        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch winnerCommitted = new CountDownLatch(1);
        AtomicReference<Throwable> winnerFailure = new AtomicReference<>();
        AtomicReference<Throwable> loserFailure = new AtomicReference<>();

        Thread winner = new Thread(() -> {
            try {
                inTransaction(() -> {
                    Order order = orderRepository.findById(orderId).orElseThrow();
                    bothRead.countDown();
                    await(bothRead);
                    order.setStatus(OrderStatus.CONFIRMED);
                    orderRepository.saveAndFlush(order);
                });
            } catch (Throwable failure) {
                winnerFailure.set(failure);
            } finally {
                winnerCommitted.countDown();
            }
        }, "order-winner");

        Thread loser = new Thread(() -> {
            try {
                inTransaction(() -> {
                    Order order = orderRepository.findById(orderId).orElseThrow();
                    bothRead.countDown();
                    await(bothRead);
                    // The loser has read version 0 and waits until the version-1
                    // update has committed, so its own write cannot succeed.
                    await(winnerCommitted);
                    order.setStatus(OrderStatus.CANCELLED);
                    orderRepository.saveAndFlush(order);
                });
            } catch (Throwable failure) {
                loserFailure.set(failure);
            }
        }, "order-loser");

        winner.start();
        loser.start();
        winner.join(TimeUnit.SECONDS.toMillis(RACE_TIMEOUT_SECONDS * 2));
        loser.join(TimeUnit.SECONDS.toMillis(RACE_TIMEOUT_SECONDS * 2));

        assertThat(winnerFailure.get()).isNull();
        assertThat(loserFailure.get()).isInstanceOf(OptimisticLockingFailureException.class);

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a concurrent payment transition fails instead of overwriting the winner, and the winner survives")
    void concurrentPaymentTransitionsConflict() throws InterruptedException {
        Payment payment = persistedPayment(persistedOrder());
        Long paymentId = payment.getId();

        CountDownLatch bothRead = new CountDownLatch(2);
        CountDownLatch winnerCommitted = new CountDownLatch(1);
        AtomicReference<Throwable> winnerFailure = new AtomicReference<>();
        AtomicReference<Throwable> loserFailure = new AtomicReference<>();

        Thread winner = new Thread(() -> {
            try {
                inTransaction(() -> {
                    Payment loaded = paymentRepository.findById(paymentId).orElseThrow();
                    bothRead.countDown();
                    await(bothRead);
                    loaded.setStatus(PaymentStatus.COMPLETED);
                    paymentRepository.saveAndFlush(loaded);
                });
            } catch (Throwable failure) {
                winnerFailure.set(failure);
            } finally {
                winnerCommitted.countDown();
            }
        }, "payment-winner");

        Thread loser = new Thread(() -> {
            try {
                inTransaction(() -> {
                    Payment loaded = paymentRepository.findById(paymentId).orElseThrow();
                    bothRead.countDown();
                    await(bothRead);
                    await(winnerCommitted);
                    loaded.setStatus(PaymentStatus.FAILED);
                    paymentRepository.saveAndFlush(loaded);
                });
            } catch (Throwable failure) {
                loserFailure.set(failure);
            }
        }, "payment-loser");

        winner.start();
        loser.start();
        winner.join(TimeUnit.SECONDS.toMillis(RACE_TIMEOUT_SECONDS * 2));
        loser.join(TimeUnit.SECONDS.toMillis(RACE_TIMEOUT_SECONDS * 2));

        assertThat(winnerFailure.get()).isNull();
        assertThat(loserFailure.get()).isInstanceOf(OptimisticLockingFailureException.class);

        Payment reloaded = paymentRepository.findById(paymentId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("sequential writes do not conflict and advance the version once per write")
    void sequentialTransitionsAdvanceTheVersion() {
        Long orderId = persistedOrder().getId();

        inTransaction(() -> orderRepository.findById(orderId).orElseThrow()
                .setStatus(OrderStatus.CONFIRMED));
        inTransaction(() -> orderRepository.findById(orderId).orElseThrow()
                .setStatus(OrderStatus.COMPLETED));

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(reloaded.getVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("a newly persisted order and payment start at version zero")
    void newRowsStartAtVersionZero() {
        Order order = persistedOrder();
        Payment payment = persistedPayment(order);

        assertThat(order.getVersion()).isZero();
        assertThat(payment.getVersion()).isZero();
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getVersion()).isZero();
    }

    /**
     * Persists a PENDING order carrying the fixture store, employee, and one
     * line - the shape the POS creation path writes.
     */
    private Order persistedOrder() {
        Order order = new Order(OrderStatus.PENDING, DiningOption.DINE_IN,
                LocalDateTime.of(2026, 10, 25, 12, 0));
        order.setStore(store);
        order.setEmployee(employee);
        order.addLine(new OrderLine(menuItem, 1, new BigDecimal("2.50")));
        return orderRepository.saveAndFlush(order);
    }

    /**
     * Persists a PENDING card payment against the given order.
     */
    private Payment persistedPayment(Order order) {
        return paymentRepository.saveAndFlush(new Payment(order, new BigDecimal("2.50"),
                PaymentMethod.CARD, PaymentStatus.PENDING, LocalDateTime.of(2026, 10, 25, 12, 1)));
    }

    /**
     * Runs an action inside its own transaction and commits it, so a conflict
     * surfaces at flush or commit rather than being silently deferred.
     * @param action the work to perform inside the transaction
     */
    private void inTransaction(Runnable action) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> action.run());
    }

    /**
     * Waits for a racer, failing loudly rather than hanging the suite when the
     * other thread never reaches the rendezvous point.
     * @param latch the latch to wait on
     */
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the concurrent transaction");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the concurrent transaction",
                    interrupted);
        }
    }
}
