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

import com.example.posapp.entity.Modifier;
import com.example.posapp.entity.ModifierGroup;
import com.example.posapp.entity.ModifierGroupAssignment;

/**
 * Integration tests for the modifier / modifier-group persistence stack
 * against a real PostgreSQL.
 * <p>
 * Flyway applies V9 to create {@code modifier}, {@code modifier_group},
 * and {@code modifier_group_assignment}; Hibernate
 * {@code ddl-auto=validate} then confirms the JPA model matches. These
 * tests check schema metadata, constraint enforcement (unique group name,
 * unique (group, modifier) pair, selection-policy checks), and the
 * reusable-modifier semantics required by the domain.
 * </p>
 * <p>
 * Cleanup uses {@code jdbcTemplate} deletes so each test starts and ends
 * without the JPA cascade or FK checks leaving residue behind.
 * </p>
 */
@SpringBootTest
@Testcontainers
class ModifierRepositoryIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds its
     * JDBC URL and credentials into the Spring environment in place of the
     * configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ModifierRepository modifierRepository;

    @Autowired
    private ModifierGroupRepository modifierGroupRepository;

    @Autowired
    private ModifierGroupAssignmentRepository assignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // --- schema metadata matches the JPA model ---

    @Test
    @DisplayName("modifier table matches the JPA model: id, name, price_adjustment, active")
    void modifierTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'modifier'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "name", "price_adjustment", "active");
        assertThat(columns.get(1))
                .containsEntry("column_name", "name")
                .containsEntry("data_type", "character varying")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(2))
                .containsEntry("column_name", "price_adjustment")
                .containsEntry("data_type", "numeric")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(3))
                .containsEntry("column_name", "active")
                .containsEntry("data_type", "boolean")
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("modifier_group table matches the JPA model: id, name, min_selections, max_selections, active")
    void modifierGroupTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'modifier_group'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "name", "min_selections", "max_selections", "active");
        assertThat(columns.get(2))
                .containsEntry("column_name", "min_selections")
                .containsEntry("data_type", "integer")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(3))
                .containsEntry("column_name", "max_selections")
                .containsEntry("data_type", "integer")
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("modifier_group_assignment table matches the JPA model: id, modifier_group_id, modifier_id, display_order")
    void assignmentTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'modifier_group_assignment'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "modifier_group_id", "modifier_id", "display_order");
        assertThat(columns.get(3))
                .containsEntry("column_name", "display_order")
                .containsEntry("data_type", "integer")
                .containsEntry("is_nullable", "NO");
    }

    // --- unique constraints ---

    @Test
    @DisplayName("modifier_group.name is unique: PostgreSQL rejects a duplicate")
    void modifierGroupNameIsEnforcedUnique() {
        ModifierGroup first = modifierGroupRepository.save(new ModifierGroup("Integration Pizza toppings", 0, 3));
        assertThatThrownBy(() -> modifierGroupRepository.save(new ModifierGroup("Integration Pizza toppings", 0, 3)))
                .isInstanceOf(DataIntegrityViolationException.class);

        deleteModifierGroupCascade(first.getId());
    }

    @Test
    @DisplayName("modifier.name is NOT unique: same label can exist twice for reuse")
    void modifierNameIsNotUnique() {
        Modifier a = modifierRepository.save(new Modifier("Integration Extra cheese", new BigDecimal("1.00")));
        Modifier b = modifierRepository.save(new Modifier("Integration Extra cheese", new BigDecimal("2.00")));

        assertThat(a.getId()).isNotEqualTo(b.getId());
        assertThat(modifierRepository.findFirstByName("Integration Extra cheese")).isPresent();

        modifierRepository.deleteById(a.getId());
        modifierRepository.deleteById(b.getId());
    }

    @Test
    @DisplayName("modifier_group_assignment (modifier_group_id, modifier_id) is unique")
    void assignmentPairIsEnforcedUnique() {
        ModifierGroup group = modifierGroupRepository.save(new ModifierGroup("Integration Pair Uniq Group", 0, 3));
        Modifier modifier = modifierRepository.save(new Modifier("Integration Pair Uniq Cheese", new BigDecimal("1.00")));
        assignmentRepository.save(new ModifierGroupAssignment(group, modifier, 1));

        assertThatThrownBy(() -> assignmentRepository.save(new ModifierGroupAssignment(group, modifier, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);

        deleteModifierGroupCascade(group.getId());
        modifierRepository.deleteById(modifier.getId());
    }

    // --- selection policy check constraints ---

    @Test
    @DisplayName("modifier_group rejects a negative min_selections at the database level")
    void negativeMinSelectionsRejectedByDbCheck() {
        // Bypass the service to reach the DB check directly.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO modifier_group (name, min_selections, max_selections, active)"
                        + " VALUES (?, ?, ?, ?)",
                "Integration Negative Min", -1, 2, true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_modifier_group_min_non_negative");
    }

    @Test
    @DisplayName("modifier_group rejects max_selections below min_selections at the database level")
    void maxBelowMinSelectionsRejectedByDbCheck() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO modifier_group (name, min_selections, max_selections, active)"
                        + " VALUES (?, ?, ?, ?)",
                "Integration MaxBelowMin", 3, 2, true))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_modifier_group_max_gte_min");
    }

    // --- foreign keys ---

    @Test
    @DisplayName("modifier_group_assignment exposes the two expected foreign keys")
    void assignmentForeignKeysExist() {
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList(
                "SELECT tc.constraint_name, ccu.table_name AS referenced_table"
                + " FROM information_schema.table_constraints tc"
                + " JOIN information_schema.constraint_column_usage ccu"
                + "   ON tc.constraint_name = ccu.constraint_name"
                + "  AND tc.constraint_schema = ccu.constraint_schema"
                + " WHERE tc.table_schema = 'public' AND tc.table_name = 'modifier_group_assignment'"
                + "   AND tc.constraint_type = 'FOREIGN KEY'"
                + " ORDER BY tc.constraint_name");

        assertThat(foreignKeys).hasSize(2);
        assertThat(foreignKeys)
                .extracting(row -> (String) row.get("constraint_name"))
                .containsExactly("fk_modifier_group_assignment_group", "fk_modifier_group_assignment_modifier");
        assertThat(foreignKeys)
                .extracting(row -> (String) row.get("referenced_table"))
                .containsExactlyInAnyOrder("modifier", "modifier_group");
    }

    @Test
    @DisplayName("a modifier still referenced by an assignment cannot be deleted")
    void assignmentBlocksModifierDeletion() {
        ModifierGroup group = modifierGroupRepository.save(new ModifierGroup("Integration Blocker Group", 0, 3));
        Modifier modifier = modifierRepository.save(new Modifier("Integration Blocker Cheese", new BigDecimal("1.00")));
        assignmentRepository.save(new ModifierGroupAssignment(group, modifier, 1));

        assertThatThrownBy(() -> modifierRepository.deleteById(modifier.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        deleteModifierGroupCascade(group.getId());
        modifierRepository.deleteById(modifier.getId());
    }

    // --- reusability and display order ---

    @Test
    @DisplayName("modifier is reusable: the same modifier can appear in multiple groups")
    void modifierIsReusableAcrossGroups() {
        ModifierGroup pizza = modifierGroupRepository.save(new ModifierGroup("Integration Reuse Pizza", 0, 3));
        ModifierGroup burger = modifierGroupRepository.save(new ModifierGroup("Integration Reuse Burger", 0, 3));
        Modifier cheese = modifierRepository.save(new Modifier("Integration Reuse Cheese", new BigDecimal("1.00")));

        ModifierGroupAssignment pizzaAssignment = assignmentRepository.save(
                new ModifierGroupAssignment(pizza, cheese, 1));
        ModifierGroupAssignment burgerAssignment = assignmentRepository.save(
                new ModifierGroupAssignment(burger, cheese, 2));

        assertThat(assignmentRepository.findByModifierGroupIdOrderByDisplayOrder(pizza.getId()))
                .extracting(assignment -> assignment.getModifier().getId())
                .containsExactly(cheese.getId());
        assertThat(assignmentRepository.findByModifierGroupIdOrderByDisplayOrder(burger.getId()))
                .extracting(assignment -> assignment.getModifier().getId())
                .containsExactly(cheese.getId());
        assertThat(pizzaAssignment.getDisplayOrder()).isEqualTo(1);
        assertThat(burgerAssignment.getDisplayOrder()).isEqualTo(2);

        deleteModifierGroupCascade(pizza.getId());
        deleteModifierGroupCascade(burger.getId());
        modifierRepository.deleteById(cheese.getId());
    }

    @Test
    @DisplayName("assignments are returned in display order regardless of insertion order")
    void assignmentsAreReturnedInDisplayOrder() {
        ModifierGroup group = modifierGroupRepository.save(new ModifierGroup("Integration Order Group", 0, 3));
        Modifier first = modifierRepository.save(new Modifier("Integration Order A", new BigDecimal("1.00")));
        Modifier second = modifierRepository.save(new Modifier("Integration Order B", new BigDecimal("2.00")));
        Modifier third = modifierRepository.save(new Modifier("Integration Order C", new BigDecimal("3.00")));

        assignmentRepository.save(new ModifierGroupAssignment(group, second, 5));
        assignmentRepository.save(new ModifierGroupAssignment(group, third, 7));
        assignmentRepository.save(new ModifierGroupAssignment(group, first, 1));

        assertThat(assignmentRepository.findByModifierGroupIdOrderByDisplayOrder(group.getId()))
                .extracting(assignment -> assignment.getModifier().getName())
                .containsExactly(
                        "Integration Order A",
                        "Integration Order B",
                        "Integration Order C");

        deleteModifierGroupCascade(group.getId());
        modifierRepository.deleteById(first.getId());
        modifierRepository.deleteById(second.getId());
        modifierRepository.deleteById(third.getId());
    }

    @Test
    @DisplayName("unassigning from one group does not delete the modifier or affect other groups")
    void unassignFromOneGroupKeepsModifierAndOtherAssignments() {
        ModifierGroup pizza = modifierGroupRepository.save(new ModifierGroup("Integration Unassign Pizza", 0, 3));
        ModifierGroup burger = modifierGroupRepository.save(new ModifierGroup("Integration Unassign Burger", 0, 3));
        Modifier cheese = modifierRepository.save(new Modifier("Integration Unassign Cheese", new BigDecimal("1.00")));

        ModifierGroupAssignment pizzaAssignment = assignmentRepository.save(
                new ModifierGroupAssignment(pizza, cheese, 1));
        assignmentRepository.save(new ModifierGroupAssignment(burger, cheese, 1));

        assignmentRepository.delete(pizzaAssignment);

        assertThat(assignmentRepository.findByModifierGroupIdOrderByDisplayOrder(pizza.getId())).isEmpty();
        assertThat(assignmentRepository.findByModifierGroupIdOrderByDisplayOrder(burger.getId())).hasSize(1);
        assertThat(modifierRepository.findById(cheese.getId())).isPresent();

        deleteModifierGroupCascade(pizza.getId());
        deleteModifierGroupCascade(burger.getId());
        modifierRepository.deleteById(cheese.getId());
    }

    @Test
    @DisplayName("deleting a modifier group removes its assignments but leaves the modifier intact")
    void deleteGroupRemovesAssignmentsButKeepsModifier() {
        ModifierGroup group = modifierGroupRepository.save(new ModifierGroup("Integration Delete Group", 0, 3));
        Modifier cheese = modifierRepository.save(new Modifier("Integration Delete Cheese", new BigDecimal("1.00")));
        assignmentRepository.save(new ModifierGroupAssignment(group, cheese, 1));

        // Simulate the JPA cascade behaviour used by the service: remove the
        // assignments first, then the group. The modifier row must survive.
        deleteModifierGroupCascade(group.getId());

        assertThat(modifierRepository.findById(cheese.getId())).isPresent();
        modifierRepository.deleteById(cheese.getId());
    }

    @Test
    @DisplayName("modifierRepository exposes name lookup helpers (name is not unique)")
    void modifierRepositoryLookupHelpers() {
        Modifier saved = modifierRepository.save(new Modifier("Integration Lookup Cheese", new BigDecimal("1.00")));

        Optional<Modifier> byName = modifierRepository.findFirstByName("Integration Lookup Cheese");
        assertThat(byName).isPresent();
        assertThat(modifierRepository.existsByName("Integration Lookup Cheese")).isTrue();

        modifierRepository.deleteById(saved.getId());
    }

    @Test
    @DisplayName("modifierGroupRepository exposes name lookup and duplicate-detection helpers")
    void modifierGroupRepositoryLookupHelpers() {
        ModifierGroup saved = modifierGroupRepository.save(new ModifierGroup("Integration Lookup Group", 0, 2));

        assertThat(modifierGroupRepository.findByName("Integration Lookup Group")).isPresent();
        assertThat(modifierGroupRepository.existsByName("Integration Lookup Group")).isTrue();
        assertThat(modifierGroupRepository.existsByNameAndIdNot("Integration Lookup Group", saved.getId()))
                .isFalse();

        deleteModifierGroupCascade(saved.getId());
    }

    /**
     * Delete every assignment attached to the group, then the group itself.
     * Used so the FK constraint from {@code modifier_group_assignment} does
     * not block cleanup.
     */
    private void deleteModifierGroupCascade(Long modifierGroupId) {
        jdbcTemplate.update("DELETE FROM modifier_group_assignment WHERE modifier_group_id = ?", modifierGroupId);
        modifierGroupRepository.deleteById(modifierGroupId);
    }
}
