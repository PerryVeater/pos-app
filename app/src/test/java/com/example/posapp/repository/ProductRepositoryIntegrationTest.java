package com.example.posapp.repository;

import java.math.BigDecimal;
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
    @DisplayName("context boots: Flyway records V1 and V2 as successful and Hibernate validate passed")
    void contextBootsWithMigratedSchema() {
        // Reaching this point proves the context started, which means Flyway
        // migrated first and ddl-auto=validate accepted the schema. Verify the
        // recorded history as well:
        List<Map<String, Object>> history = jdbcTemplate.queryForList(
                "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank");

        assertThat(history).hasSize(2);
        assertThat(history.get(0))
                .containsEntry("version", "1")
                .containsEntry("description", "create product table")
                .containsEntry("success", true);
        assertThat(history.get(1))
                .containsEntry("version", "2")
                .containsEntry("description", "add product sku and active")
                .containsEntry("success", true);
    }

    @Test
    @DisplayName("product table matches the JPA model: sku is NOT NULL varchar(64), active is NOT NULL boolean")
    void productTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable, character_maximum_length,"
                + " numeric_precision, numeric_scale, column_default"
                + " FROM information_schema.columns WHERE table_name = 'product'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "name", "price", "sku", "active");
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
        assertThat(columns.get(3))
                .containsEntry("column_name", "sku")
                .containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 64)
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(4))
                .containsEntry("column_name", "active")
                .containsEntry("data_type", "boolean")
                .containsEntry("is_nullable", "NO")
                .containsEntry("column_default", "true");
    }

    @Test
    @DisplayName("sku is unique: catalog constraint plus database rejection of duplicates")
    void skuIsEnforcedUnique() {
        // Catalog: a UNIQUE constraint covers exactly the sku column.
        List<Map<String, Object>> uniqueColumns = jdbcTemplate.queryForList(
                "SELECT kcu.column_name"
                + " FROM information_schema.table_constraints tc"
                + " JOIN information_schema.key_column_usage kcu"
                + "   ON tc.constraint_name = kcu.constraint_name"
                + "  AND tc.constraint_schema = kcu.constraint_schema"
                + " WHERE tc.table_schema = 'public' AND tc.table_name = 'product'"
                + "   AND tc.constraint_type = 'UNIQUE'");
        assertThat(uniqueColumns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("sku");

        // Behavior: PostgreSQL rejects a second product with the same sku.
        Product first = productRepository.save(
                new Product("Uniqueness Cola", "UNIQ-COLA-001", new BigDecimal("1.00"), true));
        assertThatThrownBy(() -> productRepository.save(
                new Product("Uniqueness Cola Twin", "UNIQ-COLA-001", new BigDecimal("2.00"), true)))
                .isInstanceOf(DataIntegrityViolationException.class);

        productRepository.deleteById(first.getId());
    }

    @Test
    @DisplayName("repository performs full CRUD against PostgreSQL, persisting sku and active")
    void repositoryCrudAgainstPostgres() {
        // create
        Product saved = productRepository.save(
                new Product("Integration Cola", "INT-COLA-001", new BigDecimal("2.50"), true));
        assertThat(saved.getId()).isNotNull();

        // read: identity, lifecycle state, and money survive the round-trip
        Optional<Product> loaded = productRepository.findById(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getName()).isEqualTo("Integration Cola");
        assertThat(loaded.get().getSku()).isEqualTo("INT-COLA-001");
        assertThat(loaded.get().isActive()).isTrue();
        assertThat(loaded.get().getPrice()).isEqualByComparingTo("2.50");
        assertThat(loaded.get().getPrice()).hasScaleOf(2);

        // update (including deactivating the product instead of deleting it)
        loaded.get().setName("Integration Cola Light");
        loaded.get().setSku("INT-COLA-002");
        loaded.get().setPrice(new BigDecimal("3.00"));
        loaded.get().setActive(false);
        Product updated = productRepository.save(loaded.get());
        assertThat(updated.getName()).isEqualTo("Integration Cola Light");
        assertThat(updated.getSku()).isEqualTo("INT-COLA-002");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getPrice()).isEqualByComparingTo("3.00");

        // lookup independent of the DataLoader seed row
        assertThat(productRepository.findByName("Integration Cola Light")).hasSize(1);

        // delete
        productRepository.deleteById(saved.getId());
        assertThat(productRepository.findById(saved.getId())).isEmpty();
        assertThat(productRepository.findByName("Integration Cola Light")).isEmpty();
    }
}
