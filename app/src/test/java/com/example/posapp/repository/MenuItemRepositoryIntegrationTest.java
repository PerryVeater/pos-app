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

import com.example.posapp.entity.Category;
import com.example.posapp.entity.MenuItem;

/**
 * Integration tests for the persistence stack against a real PostgreSQL.
 * <p>
 * Each test run boots the full application context against a disposable
 * PostgreSQL 16 container managed by Testcontainers: Flyway applies the
 * migrations (V1-V3) first, then Hibernate {@code ddl-auto=validate} verifies
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
class MenuItemRepositoryIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds its
     * JDBC URL and credentials into the Spring environment in place of the
     * configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private CategoryRepository categoryRepository;

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
    @DisplayName("product table matches the JPA model: sku NOT NULL, active NOT NULL, category_id nullable")
    void productTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable, character_maximum_length,"
                + " numeric_precision, numeric_scale, column_default"
                + " FROM information_schema.columns WHERE table_name = 'product'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "name", "price", "sku", "active", "category_id");
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
        assertThat(columns.get(5))
                .containsEntry("column_name", "category_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "YES");
    }

    @Test
    @DisplayName("category table matches the JPA model: name is NOT NULL varchar(255)")
    void categoryTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable, character_maximum_length"
                + " FROM information_schema.columns WHERE table_name = 'category'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "name");
        assertThat(columns.get(0))
                .containsEntry("column_name", "id")
                .containsEntry("data_type", "bigint");
        assertThat(columns.get(1))
                .containsEntry("column_name", "name")
                .containsEntry("data_type", "character varying")
                .containsEntry("character_maximum_length", 255)
                .containsEntry("is_nullable", "NO");
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
        MenuItem first = menuItemRepository.save(
                new MenuItem("Uniqueness Cola", "UNIQ-COLA-001", new BigDecimal("1.00"), true));
        assertThatThrownBy(() -> menuItemRepository.save(
                new MenuItem("Uniqueness Cola Twin", "UNIQ-COLA-001", new BigDecimal("2.00"), true)))
                .isInstanceOf(DataIntegrityViolationException.class);

        menuItemRepository.deleteById(first.getId());
    }

    @Test
    @DisplayName("category name is unique: catalog constraint plus database rejection of duplicates")
    void categoryNameIsEnforcedUnique() {
        // Catalog: a UNIQUE constraint covers exactly the name column.
        List<Map<String, Object>> uniqueColumns = jdbcTemplate.queryForList(
                "SELECT kcu.column_name"
                + " FROM information_schema.table_constraints tc"
                + " JOIN information_schema.key_column_usage kcu"
                + "   ON tc.constraint_name = kcu.constraint_name"
                + "  AND tc.constraint_schema = kcu.constraint_schema"
                + " WHERE tc.table_schema = 'public' AND tc.table_name = 'category'"
                + "   AND tc.constraint_type = 'UNIQUE'");
        assertThat(uniqueColumns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("name");

        // Behavior: PostgreSQL rejects a second category with the same name.
        Category first = categoryRepository.save(new Category("Integration Drinks"));
        assertThatThrownBy(() -> categoryRepository.save(new Category("Integration Drinks")))
                .isInstanceOf(DataIntegrityViolationException.class);

        categoryRepository.deleteById(first.getId());
    }

    @Test
    @DisplayName("product.category_id is a foreign key to category(id), enforced by PostgreSQL")
    void productCategoryForeignKeyIsEnforced() {
        // Catalog: exactly one FK on product, category_id -> category.id.
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
                + " WHERE tc.table_schema = 'public' AND tc.table_name = 'product'"
                + "   AND tc.constraint_type = 'FOREIGN KEY'");
        assertThat(foreignKeys).hasSize(1);
        assertThat(foreignKeys.get(0))
                .containsEntry("constraint_name", "fk_product_category")
                .containsEntry("column_name", "category_id")
                .containsEntry("referenced_table", "category")
                .containsEntry("referenced_column", "id");

        // Behavior: a product cannot reference a nonexistent category.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO product (name, sku, price, active, category_id) VALUES (?, ?, ?, ?, ?)",
                "FK Cola", "FK-COLA-001", new BigDecimal("1.00"), true, 999999L))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Behavior: a category still referenced by a product cannot be deleted.
        Category drinks = categoryRepository.save(new Category("FK Drinks"));
        MenuItem cola = new MenuItem("FK Cola", "FK-COLA-002", new BigDecimal("1.00"), true);
        cola.setCategory(drinks);
        menuItemRepository.save(cola);

        assertThatThrownBy(() -> categoryRepository.deleteById(drinks.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: once the product is gone, the category deletes fine.
        menuItemRepository.deleteById(cola.getId());
        categoryRepository.deleteById(drinks.getId());
        assertThat(categoryRepository.findById(drinks.getId())).isEmpty();
    }

    @Test
    @DisplayName("repository performs full CRUD against PostgreSQL, persisting category and lifecycle state")
    void repositoryCrudAgainstPostgres() {
        // create (with a category reference)
        Category category = categoryRepository.save(new Category("Integration Drinks"));
        MenuItem cola = new MenuItem("Integration Cola", "INT-COLA-001", new BigDecimal("2.50"), true);
        cola.setCategory(category);
        MenuItem saved = menuItemRepository.save(cola);
        assertThat(saved.getId()).isNotNull();

        // read: identity, category, lifecycle state, and money survive the round-trip
        Optional<MenuItem> loaded = menuItemRepository.findById(saved.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getName()).isEqualTo("Integration Cola");
        assertThat(loaded.get().getSku()).isEqualTo("INT-COLA-001");
        assertThat(loaded.get().isActive()).isTrue();
        assertThat(loaded.get().getPrice()).isEqualByComparingTo("2.50");
        assertThat(loaded.get().getPrice()).hasScaleOf(2);
        assertThat(loaded.get().getCategory()).isNotNull();
        assertThat(loaded.get().getCategory().getId()).isEqualTo(category.getId());
        assertThat(loaded.get().getCategory().getName()).isEqualTo("Integration Drinks");

        // update (including clearing the category)
        loaded.get().setName("Integration Cola Light");
        loaded.get().setSku("INT-COLA-002");
        loaded.get().setPrice(new BigDecimal("3.00"));
        loaded.get().setActive(false);
        loaded.get().setCategory(null);
        MenuItem updated = menuItemRepository.save(loaded.get());
        assertThat(updated.getName()).isEqualTo("Integration Cola Light");
        assertThat(updated.getSku()).isEqualTo("INT-COLA-002");
        assertThat(updated.isActive()).isFalse();
        assertThat(updated.getPrice()).isEqualByComparingTo("3.00");
        assertThat(updated.getCategory()).isNull();
        assertThat(menuItemRepository.findById(saved.getId()).orElseThrow().getCategory()).isNull();

        // lookup independent of the DataLoader seed row
        assertThat(menuItemRepository.findByName("Integration Cola Light")).hasSize(1);

        // delete
        menuItemRepository.deleteById(saved.getId());
        assertThat(menuItemRepository.findById(saved.getId())).isEmpty();
        assertThat(menuItemRepository.findByName("Integration Cola Light")).isEmpty();
        categoryRepository.deleteById(category.getId());
    }
}
