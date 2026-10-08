package com.example.posapp.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.posapp.entity.Product;

/**
 * Integration tests for the persistence stack against a real PostgreSQL.
 * <p>
 * Each test run boots the full application context against a disposable
 * PostgreSQL 16 container managed by Testcontainers: Flyway applies the
 * migrations first, then Hibernate {@code ddl-auto=validate} verifies the
 * schema before the context starts. The container gets a random port, so it
 * never collides with the Docker Compose development database.
 * </p>
 * <p>
 * The {@link com.example.posapp.loader.DataLoader} seed runs once during
 * context startup, so tests assert only on rows they create themselves and
 * on schema metadata, never on global row counts.
 * </p>
 */
@SpringBootTest
@Testcontainers
class ProductRepositoryIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds its
     * JDBC URL and credentials into the Spring environment in place of the
     * configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("context boots: Flyway records a successful V1 and Hibernate validate passed")
    void contextBootsWithMigratedSchema() {
        // Reaching this point proves the context started, which means Flyway
        // migrated first and ddl-auto=validate accepted the schema. Verify the
        // recorded history as well:
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history).hasSize(1);
        assertThat(history.get(0))
                .containsEntry("version", "1")
                .containsEntry("description", "create product table")
                .containsEntry("success", true);
    }

    @Test
    @DisplayName("product table has the expected columns, including NUMERIC(10,2) price")
    void productTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, numeric_precision, numeric_scale"
                + " FROM information_schema.columns WHERE table_name = 'product'"
                + " ORDER BY ordinal_position");

        assertThat(columns).hasSize(3);
        assertThat(columns.get(0))
                .containsEntry("column_name", "id")
                .containsEntry("data_type", "bigint");
        assertThat(columns.get(1))
                .containsEntry("column_name", "name")
                .containsEntry("data_type", "character varying");
        assertThat(columns.get(2))
                .containsEntry("column_name", "price")
                .containsEntry("data_type", "numeric")
                .containsEntry("numeric_precision", 10)
                .containsEntry("numeric_scale", 2);
    }

    @Test
    @DisplayName("repository performs full CRUD against PostgreSQL")
    void repositoryCrudAgainstPostgres() {
        // create
        Product saved = productRepository.save(new Product("Integration Cola", new BigDecimal("2.50")));
        assertThat(saved.getId()).isNotNull();

        // read: money survives the round-trip with exact scale
        Optional<Product> loaded = productRepository.findById(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getName()).isEqualTo("Integration Cola");
        assertThat(loaded.get().getPrice()).isEqualByComparingTo("2.50");
        assertThat(loaded.get().getPrice()).hasScaleOf(2);

        // update
        loaded.get().setName("Integration Cola Light");
        loaded.get().setPrice(new BigDecimal("3.00"));
        Product updated = productRepository.save(loaded.get());
        assertThat(updated.getName()).isEqualTo("Integration Cola Light");
        assertThat(updated.getPrice()).isEqualByComparingTo("3.00");

        // lookup independent of the DataLoader seed row
        assertThat(productRepository.findByName("Integration Cola Light")).hasSize(1);

        // delete
        productRepository.deleteById(saved.getId());
        assertThat(productRepository.findById(saved.getId())).isEmpty();
        assertThat(productRepository.findByName("Integration Cola Light")).isEmpty();
    }
}
