package com.example.posapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integration tests for the V22 query indexes (defect D4).
 * <p>
 * Two things are worth proving about an index: that it is the index you meant
 * to write, and that the planner uses it. The first is schema metadata; the
 * second only shows up on a dataset large enough for a sequential scan to stop
 * being the cheapest option, which is why this class seeds one before asking
 * {@code EXPLAIN}. A handful of rows would prove nothing - PostgreSQL reads a
 * four-row table from memory no matter what is indexed.
 * </p>
 * <p>
 * The plans asserted here are also the recorded evidence for the M2 acceptance
 * criterion ("EXPLAIN shows index scans, not sequential scans"), so a change
 * that quietly reintroduces a scan fails the build rather than a review.
 * </p>
 * <p>
 * Seed sizes are chosen per index, not arbitrarily: the two child lookups
 * ({@code order_line.order_id}, {@code payments.order_id}) return one to four
 * rows out of tens of thousands, and the three less selective paths
 * ({@code orders.store_id}, {@code orders.employee_id},
 * {@code order_line.product_id}) each touch under two percent of their table.
 * At those selectivities the planner has no reason to prefer a scan, so the
 * assertions are stable rather than luck.
 * </p>
 */
@SpringBootTest
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderQueryIndexIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds its
     * JDBC URL and credentials into the Spring environment in place of the
     * configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Stores to spread orders across: 6,000 orders over 30 stores. */
    private static final int STORES = 30;

    /** Employees to spread orders across: 6,000 orders over 100 employees. */
    private static final int EMPLOYEES = 100;

    /** Menu items to spread order lines across: 24,000 lines over 200 items. */
    private static final int MENU_ITEMS = 200;

    /** Orders to seed; each carries four lines and one payment. */
    private static final int ORDERS = 6_000;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> storeIds = new ArrayList<>();
    private final List<Long> employeeIds = new ArrayList<>();
    private final List<Long> menuItemIds = new ArrayList<>();
    private Long firstOrderId;

    /**
     * Seeds the dataset once for the class: parent rows through returned ids so
     * the distribution is explicit, then the three high-volume tables through
     * {@code generate_series} so the seed costs one round trip each rather than
     * thirty thousand.
     */
    @BeforeAll
    void seedDataset() {
        Long organizationId = insert(
                "INSERT INTO organization (name) VALUES (?) RETURNING id", "Order Index IT");

        for (int i = 0; i < STORES; i++) {
            storeIds.add(insert("INSERT INTO stores (organization_id, store_number, name, country,"
                    + " state_province, city, address_line1, postal_code, timezone)"
                    + " VALUES (?, ?, ?, 'US', 'CA', 'Los Angeles', ?, ?, 'America/Los_Angeles')"
                    + " RETURNING id",
                    organizationId, String.valueOf(i), "Store " + i,
                    (i + 1) + " Main St", "900" + (10 + i)));
        }

        for (int i = 0; i < EMPLOYEES; i++) {
            employeeIds.add(insert(
                    "INSERT INTO employee (organization_id, name, email) VALUES (?, ?, ?) RETURNING id",
                    organizationId, "Employee " + i, "employee" + i + "@order-index.example.com"));
        }

        for (int i = 0; i < MENU_ITEMS; i++) {
            menuItemIds.add(insert(
                    "INSERT INTO product (name, sku, price, active) VALUES (?, ?, ?, true) RETURNING id",
                    "Item " + i, "ORDER-INDEX-" + i, new BigDecimal("5.00")));
        }

        jdbcTemplate.execute("INSERT INTO orders (status, created_at, dining_option, store_id, employee_id)"
                + " SELECT 'COMPLETED', TIMESTAMPTZ '2026-01-01 00:00:00+00' + (g * INTERVAL '1 minute'),"
                + " 'DINE_IN',"
                + " s.ids[1 + ((g - 1) % array_length(s.ids, 1))],"
                + " e.ids[1 + ((g - 1) % array_length(e.ids, 1))]"
                + " FROM generate_series(1, " + ORDERS + ") AS g"
                + " CROSS JOIN (SELECT " + arrayLiteral(storeIds) + " AS ids) AS s"
                + " CROSS JOIN (SELECT " + arrayLiteral(employeeIds) + " AS ids) AS e");

        jdbcTemplate.execute("INSERT INTO order_line (order_id, product_id, quantity, unit_price)"
                + " SELECT o.id, p.ids[1 + ((o.id + k) % array_length(p.ids, 1))], 1, 5.00"
                + " FROM orders o"
                + " CROSS JOIN generate_series(1, 4) AS k"
                + " CROSS JOIN (SELECT " + arrayLiteral(menuItemIds) + " AS ids) AS p");

        jdbcTemplate.execute("INSERT INTO payments (order_id, amount, method, status, created_at)"
                + " SELECT o.id, 5.00, 'CARD', 'COMPLETED', o.created_at FROM orders o");

        // The planner reads these statistics, not the rows, so without ANALYZE
        // it would be planning against an empty-table estimate.
        jdbcTemplate.execute("ANALYZE orders, order_line, payments, product, employee, stores");

        firstOrderId = jdbcTemplate.queryForObject("SELECT min(id) FROM orders", Long.class);
    }

    /**
     * Removes the seeded dataset so the shared container's other test classes,
     * and any later run in the same JVM, start from the state they expect.
     */
    @AfterAll
    void clearSeededDataset() {
        // Children before parents: lines and payments reference the orders,
        // lines reference the products.
        jdbcTemplate.execute("DELETE FROM order_line");
        jdbcTemplate.execute("DELETE FROM payments");
        jdbcTemplate.execute("DELETE FROM orders");
        jdbcTemplate.execute("DELETE FROM product");
        jdbcTemplate.execute("DELETE FROM employee");
        jdbcTemplate.execute("DELETE FROM stores");
        jdbcTemplate.execute("DELETE FROM organization");
    }

    @Test
    @DisplayName("V22 adds each index on the columns its query filters and orders by")
    void indexDefinitionsMatchTheQueriesTheyServe() {
        assertThat(indexDefinition("idx_orders_store_created_at"))
                .contains("ON public.orders USING btree (store_id, created_at)");
        assertThat(indexDefinition("idx_orders_employee_id"))
                .contains("ON public.orders USING btree (employee_id)");
        assertThat(indexDefinition("idx_payment_order_id"))
                .contains("ON public.payments USING btree (order_id)");
        assertThat(indexDefinition("idx_order_line_order_id"))
                .contains("ON public.order_line USING btree (order_id)");
        assertThat(indexDefinition("idx_order_line_product_id"))
                .contains("ON public.order_line USING btree (product_id)");
    }

    @Test
    @DisplayName("one order's lines are found through idx_order_line_order_id, not by scanning order_line")
    void orderLinesAreReachedThroughTheirOrderIndex() {
        String plan = explain("SELECT id FROM order_line WHERE order_id = " + firstOrderId);

        assertThat(plan).contains("idx_order_line_order_id");
        assertThat(plan).doesNotContain("Seq Scan on order_line");
    }

    @Test
    @DisplayName("an order's payments are found through idx_payment_order_id, not by scanning payments")
    void paymentsAreReachedThroughTheirOrderIndex() {
        String plan = explain("SELECT id FROM payments WHERE order_id = " + firstOrderId);

        assertThat(plan).contains("idx_payment_order_id");
        assertThat(plan).doesNotContain("Seq Scan on payments");
    }

    @Test
    @DisplayName("a store's recent orders come back sorted from idx_orders_store_created_at, with no sort step")
    void storeHistoryIsOrderedByTheCompositeIndex() {
        String plan = explain("SELECT id, created_at FROM orders WHERE store_id = " + storeIds.get(0)
                + " ORDER BY created_at DESC LIMIT 50");

        assertThat(plan).contains("idx_orders_store_created_at");
        assertThat(plan).doesNotContain("Seq Scan on orders");
        // The point of putting created_at in the index: the LIMIT can stop the
        // index walk instead of the plan sorting the store's whole history.
        assertThat(plan).doesNotContain("Sort");
    }

    @Test
    @DisplayName("an employee's orders are found through idx_orders_employee_id, not by scanning orders")
    void employeeHistoryUsesItsIndex() {
        String plan = explain("SELECT id FROM orders WHERE employee_id = " + employeeIds.get(0));

        assertThat(plan).contains("idx_orders_employee_id");
        assertThat(plan).doesNotContain("Seq Scan on orders");
    }

    @Test
    @DisplayName("an item's sales lines are found through idx_order_line_product_id, not by scanning order_line")
    void itemSalesUseTheirIndex() {
        String plan = explain("SELECT id FROM order_line WHERE product_id = " + menuItemIds.get(0));

        assertThat(plan).contains("idx_order_line_product_id");
        assertThat(plan).doesNotContain("Seq Scan on order_line");
    }

    /**
     * Runs {@code EXPLAIN} for a query and returns the plan as one string, so a
     * failure message shows the whole plan rather than the first line of it.
     */
    private String explain(String query) {
        return String.join("\n", jdbcTemplate.queryForList("EXPLAIN " + query, String.class));
    }

    /**
     * Reads the stored definition of an index from {@code pg_indexes}; the
     * column list in that string is the same one the query planner sees.
     */
    private String indexDefinition(String indexName) {
        return jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = ?", String.class, indexName);
    }

    /**
     * Inserts a row and returns its generated id, so seeded parents can be
     * referenced explicitly instead of relying on ids being contiguous.
     */
    private Long insert(String sql, Object... args) {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    /**
     * Renders ids as a PostgreSQL {@code bigint} array literal for the seed
     * statements. Only values this class generated are ever interpolated.
     */
    private static String arrayLiteral(List<Long> ids) {
        return ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "ARRAY[", "]::bigint[]"));
    }
}
