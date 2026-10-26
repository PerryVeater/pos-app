package com.example.posapp.migration;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the {@code V24} backfill on data that actually needed converting.
 * <p>
 * Every other test runs the whole migration chain against an empty database,
 * where a data migration has nothing to do and therefore cannot fail. This one
 * builds the opposite situation: a scratch database is migrated to {@code V23}
 * - the last version with naive timestamps - seeded with two stores in
 * different zones, an order and a payment at each, and only then migrated to
 * the head of the chain.
 * </p>
 * <p>
 * The scratch database exists so the probe does not disturb the schema the
 * other tests share, and so {@code V24} can be reached from a populated
 * {@code V23} rather than from nothing. It is dropped afterwards.
 * </p>
 */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BusinessTimestampMigrationIntegrationTest {

    private static final String PROBE_DATABASE = "timestamps_probe";

    /** 12:00 on 2026-10-25 in Denver is 18:00 UTC (MDT, UTC-6). */
    private static final Instant DENVER_ORDER = Instant.parse("2026-10-25T18:00:00Z");

    /** 12:00 on 2026-10-25 in Auckland is 23:00 UTC the day before (NZDT, UTC+13). */
    private static final Instant AUCKLAND_ORDER = Instant.parse("2026-10-24T23:00:00Z");

    /** Five minutes after each order, in the same store's zone. */
    private static final Instant DENVER_PAYMENT = Instant.parse("2026-10-25T18:05:00Z");
    private static final Instant AUCKLAND_PAYMENT = Instant.parse("2026-10-24T23:05:00Z");

    /**
     * Disposable PostgreSQL 16 database; used directly rather than through
     * {@code @ServiceConnection} because this test needs no application context.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private JdbcTemplate probe;
    private String probeUrl;
    private String adminUrl;

    private long denverOrderId;
    private long aucklandOrderId;
    private long denverPaymentId;
    private long aucklandPaymentId;

    @BeforeAll
    void seedATwentyThreeDatabaseAndMigrateIt() {
        adminUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/postgres";
        new JdbcTemplate(dataSource(adminUrl)).execute("CREATE DATABASE " + PROBE_DATABASE);

        probeUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432)
                + "/" + PROBE_DATABASE;
        probe = new JdbcTemplate(dataSource(probeUrl));

        migrate("23");
        seedNaiveHistory();
        migrate(null);
    }

    @AfterAll
    void dropTheProbeDatabase() {
        new JdbcTemplate(dataSource(adminUrl))
                .execute("DROP DATABASE IF EXISTS " + PROBE_DATABASE + " WITH (FORCE)");
    }

    @Test
    @DisplayName("V24 reinterprets each order's wall-clock time in its own store's zone, not the server's")
    void backfillsEachOrderInItsStoreTimezone() {
        assertThat(createdAt("orders", denverOrderId)).isEqualTo(DENVER_ORDER);
        assertThat(createdAt("orders", aucklandOrderId)).isEqualTo(AUCKLAND_ORDER);
        assertThat(probe.queryForObject("SELECT count(*) FROM orders", Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("V24 reaches a payment's zone through its order, since a payment has no store of its own")
    void backfillsEachPaymentThroughItsOrdersStore() {
        assertThat(createdAt("payments", denverPaymentId)).isEqualTo(DENVER_PAYMENT);
        assertThat(createdAt("payments", aucklandPaymentId)).isEqualTo(AUCKLAND_PAYMENT);
    }

    @Test
    @DisplayName("V24 leaves both columns TIMESTAMPTZ and recreates the history index it had to drop")
    void keepsTheColumnTypeAndTheHistoryIndex() {
        assertThat(dataType("orders")).isEqualTo("timestamp with time zone");
        assertThat(dataType("payments")).isEqualTo("timestamp with time zone");

        List<String> indexes = probe.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'orders'", String.class);
        assertThat(indexes)
                .anySatisfy(definition -> assertThat(definition)
                        .contains("idx_orders_store_created_at")
                        .contains("(store_id, created_at)"));
    }

    /**
     * Migrates the probe database to the head of the chain, or to {@code version}
     * when one is given.
     */
    private void migrate(String version) {
        var configuration = Flyway.configure()
                .dataSource(probeUrl, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (version != null) {
            configuration = configuration.target(MigrationVersion.fromVersion(version));
        }
        configuration.load().migrate();
    }

    /**
     * Writes the history {@code V24} has to convert: naive timestamps that mean
     * noon at the store, in two zones 19 hours apart.
     */
    private void seedNaiveHistory() {
        long organizationId = insert("INSERT INTO organization (name, active) VALUES ('Probe chain', true)");
        long denverId = insert("INSERT INTO stores (organization_id, name, active, country,"
                + " state_province, city, address_line1, postal_code, timezone)"
                + " VALUES (?, 'Denver', true, 'US', 'CO', 'Denver', '1 Main St', '80202',"
                + " 'America/Denver')", organizationId);
        long aucklandId = insert("INSERT INTO stores (organization_id, name, active, country,"
                + " state_province, city, address_line1, postal_code, timezone)"
                + " VALUES (?, 'Auckland', true, 'NZ', 'AUK', 'Auckland', '1 Queen St', '1010',"
                + " 'Pacific/Auckland')", organizationId);
        long employeeId = insert("INSERT INTO employee (organization_id, name, active)"
                + " VALUES (?, 'Probe Barista', true)", organizationId);

        denverOrderId = insert("INSERT INTO orders (status, created_at, dining_option, store_id, employee_id)"
                + " VALUES ('PENDING', TIMESTAMP '2026-10-25 12:00:00', 'DINE_IN', ?, ?)",
                denverId, employeeId);
        aucklandOrderId = insert("INSERT INTO orders (status, created_at, dining_option, store_id, employee_id)"
                + " VALUES ('PENDING', TIMESTAMP '2026-10-25 12:00:00', 'DINE_IN', ?, ?)",
                aucklandId, employeeId);

        denverPaymentId = insert("INSERT INTO payments (order_id, amount, method, status, created_at)"
                + " VALUES (?, 3.50, 'CARD', 'PENDING', TIMESTAMP '2026-10-25 12:05:00')", denverOrderId);
        aucklandPaymentId = insert("INSERT INTO payments (order_id, amount, method, status, created_at)"
                + " VALUES (?, 3.50, 'CARD', 'PENDING', TIMESTAMP '2026-10-25 12:05:00')", aucklandOrderId);
    }

    /**
     * Runs an insert and returns the generated ID.
     */
    private long insert(String sql, Object... args) {
        Long id = probe.queryForObject(sql + " RETURNING id", Long.class, args);
        assertThat(id).isNotNull();
        return id;
    }

    /**
     * Reads a converted timestamp back as an instant.
     * @param table either {@code orders} or {@code payments}
     * @param id the row to read
     */
    private Instant createdAt(String table, long id) {
        OffsetDateTime value = probe.queryForObject(
                "SELECT created_at FROM " + table + " WHERE id = ?", OffsetDateTime.class, id);
        assertThat(value).isNotNull();
        return value.toInstant();
    }

    /**
     * Reads the declared type of {@code created_at}.
     */
    private String dataType(String table) {
        return probe.queryForObject(
                "SELECT data_type FROM information_schema.columns"
                        + " WHERE table_name = ? AND column_name = 'created_at'",
                String.class, table);
    }

    private DriverManagerDataSource dataSource(String url) {
        return new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
    }
}
