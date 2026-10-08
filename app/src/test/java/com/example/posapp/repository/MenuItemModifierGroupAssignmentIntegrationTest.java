package com.example.posapp.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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

import com.example.posapp.entity.MenuItem;
import com.example.posapp.entity.MenuItemModifierGroupAssignment;
import com.example.posapp.entity.ModifierGroup;

/**
 * Integration tests for the MenuItem ↔ ModifierGroup assignment stack
 * against a real PostgreSQL.
 * <p>
 * Flyway applies V10 to create {@code menu_item_modifier_group_assignment};
 * Hibernate {@code ddl-auto=validate} then confirms the JPA model matches.
 * These tests check schema metadata, constraint enforcement (unique pair
 * and both foreign keys), and the cross-domain reuse semantics required by
 * the domain: a ModifierGroup can appear on multiple MenuItems, a MenuItem
 * can carry multiple ModifierGroups, and unassigning leaves both sides
 * intact.
 * </p>
 */
@SpringBootTest
@Testcontainers
class MenuItemModifierGroupAssignmentIntegrationTest {

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
    private ModifierGroupRepository modifierGroupRepository;

    @Autowired
    private MenuItemModifierGroupAssignmentRepository assignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // --- schema metadata matches the JPA model ---

    @Test
    @DisplayName("menu_item_modifier_group_assignment table matches the JPA model: id, menu_item_id, modifier_group_id, display_order")
    void assignmentTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'menu_item_modifier_group_assignment'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "menu_item_id", "modifier_group_id", "display_order");
        assertThat(columns.get(1))
                .containsEntry("column_name", "menu_item_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(2))
                .containsEntry("column_name", "modifier_group_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(3))
                .containsEntry("column_name", "display_order")
                .containsEntry("data_type", "integer")
                .containsEntry("is_nullable", "NO");
    }

    // --- unique constraint ---

    @Test
    @DisplayName("menu_item_modifier_group_assignment (menu_item_id, modifier_group_id) is unique")
    void assignmentPairIsEnforcedUnique() {
        MenuItem item = menuItemRepository.save(
                new MenuItem("INT-MIMG-Uniq Pizza", "INT-MIMG-UNIQ-PIZZA", new BigDecimal("9.00"), true));
        ModifierGroup group = modifierGroupRepository.save(new ModifierGroup("INT-MIMG Uniq Toppings", 0, 3));
        assignmentRepository.save(new MenuItemModifierGroupAssignment(item, group, 1));

        assertThatThrownBy(() -> assignmentRepository.save(
                new MenuItemModifierGroupAssignment(item, group, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);

        deleteMenuItemModifierGroups(item.getId());
        deleteModifierGroupCascade(group.getId());
        menuItemRepository.deleteById(item.getId());
    }

    // --- foreign keys ---

    @Test
    @DisplayName("menu_item_modifier_group_assignment exposes the two expected foreign keys")
    void assignmentForeignKeysExist() {
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList(
                "SELECT tc.constraint_name, ccu.table_name AS referenced_table"
                + " FROM information_schema.table_constraints tc"
                + " JOIN information_schema.constraint_column_usage ccu"
                + "   ON tc.constraint_name = ccu.constraint_name"
                + "  AND tc.constraint_schema = ccu.constraint_schema"
                + " WHERE tc.table_schema = 'public'"
                + "   AND tc.table_name = 'menu_item_modifier_group_assignment'"
                + "   AND tc.constraint_type = 'FOREIGN KEY'"
                + " ORDER BY tc.constraint_name");

        assertThat(foreignKeys).hasSize(2);
        assertThat(foreignKeys)
                .extracting(row -> (String) row.get("constraint_name"))
                .containsExactly("fk_mimg_assignment_group", "fk_mimg_assignment_item");
        // Note: MenuItem maps to the legacy `product` table, so the item FK
        // targets product(id), not menu_item(id).
        assertThat(foreignKeys)
                .extracting(row -> (String) row.get("referenced_table"))
                .containsExactlyInAnyOrder("modifier_group", "product");
    }

    @Test
    @DisplayName("a ModifierGroup still referenced by an assignment cannot be deleted")
    void assignmentBlocksModifierGroupDeletion() {
        MenuItem item = menuItemRepository.save(
                new MenuItem("INT-MIMG-Blocker Pizza", "INT-MIMG-BLOCKER-PIZZA", new BigDecimal("9.00"), true));
        ModifierGroup group = modifierGroupRepository.save(new ModifierGroup("INT-MIMG Blocker Toppings", 0, 3));
        assignmentRepository.save(new MenuItemModifierGroupAssignment(item, group, 1));

        assertThatThrownBy(() -> modifierGroupRepository.deleteById(group.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        deleteMenuItemModifierGroups(item.getId());
        deleteModifierGroupCascade(group.getId());
        menuItemRepository.deleteById(item.getId());
    }

    // --- reusability and display order ---

    @Test
    @DisplayName("a ModifierGroup can be assigned to multiple MenuItems (reusability)")
    void modifierGroupIsReusableAcrossMenuItems() {
        MenuItem pizza = menuItemRepository.save(
                new MenuItem("INT-MIMG Reuse Pizza", "INT-MIMG-REUSE-PIZZA", new BigDecimal("9.00"), true));
        MenuItem pasta = menuItemRepository.save(
                new MenuItem("INT-MIMG Reuse Pasta", "INT-MIMG-REUSE-PASTA", new BigDecimal("8.00"), true));
        ModifierGroup toppings = modifierGroupRepository.save(new ModifierGroup("INT-MIMG Reuse Toppings", 0, 3));

        MenuItemModifierGroupAssignment pizzaAssignment = assignmentRepository.save(
                new MenuItemModifierGroupAssignment(pizza, toppings, 1));
        MenuItemModifierGroupAssignment pastaAssignment = assignmentRepository.save(
                new MenuItemModifierGroupAssignment(pasta, toppings, 2));

        assertThat(assignmentRepository.findByMenuItemIdOrderByDisplayOrder(pizza.getId()))
                .extracting(assignment -> assignment.getModifierGroup().getId())
                .containsExactly(toppings.getId());
        assertThat(assignmentRepository.findByMenuItemIdOrderByDisplayOrder(pasta.getId()))
                .extracting(assignment -> assignment.getModifierGroup().getId())
                .containsExactly(toppings.getId());
        assertThat(pizzaAssignment.getDisplayOrder()).isEqualTo(1);
        assertThat(pastaAssignment.getDisplayOrder()).isEqualTo(2);

        deleteMenuItemModifierGroups(pizza.getId());
        deleteMenuItemModifierGroups(pasta.getId());
        deleteModifierGroupCascade(toppings.getId());
        menuItemRepository.deleteById(pizza.getId());
        menuItemRepository.deleteById(pasta.getId());
    }

    @Test
    @DisplayName("a MenuItem can carry multiple ModifierGroups and returns them in display order")
    void menuItemCarriesMultipleModifierGroupsInDisplayOrder() {
        MenuItem item = menuItemRepository.save(
                new MenuItem("INT-MIMG Order Pizza", "INT-MIMG-ORDER-PIZZA", new BigDecimal("9.00"), true));
        ModifierGroup first = modifierGroupRepository.save(new ModifierGroup("INT-MIMG Order A", 0, 2));
        ModifierGroup second = modifierGroupRepository.save(new ModifierGroup("INT-MIMG Order B", 0, 2));
        ModifierGroup third = modifierGroupRepository.save(new ModifierGroup("INT-MIMG Order C", 0, 2));

        assignmentRepository.save(new MenuItemModifierGroupAssignment(item, second, 5));
        assignmentRepository.save(new MenuItemModifierGroupAssignment(item, third, 7));
        assignmentRepository.save(new MenuItemModifierGroupAssignment(item, first, 1));

        assertThat(assignmentRepository.findByMenuItemIdOrderByDisplayOrder(item.getId()))
                .extracting(assignment -> assignment.getModifierGroup().getName())
                .containsExactly(
                        "INT-MIMG Order A",
                        "INT-MIMG Order B",
                        "INT-MIMG Order C");

        deleteMenuItemModifierGroups(item.getId());
        deleteModifierGroupCascade(first.getId());
        deleteModifierGroupCascade(second.getId());
        deleteModifierGroupCascade(third.getId());
        menuItemRepository.deleteById(item.getId());
    }

    @Test
    @DisplayName("removing an assignment does not delete the MenuItem or the ModifierGroup")
    void unassignKeepsBothUnderlyingRecords() {
        MenuItem item = menuItemRepository.save(
                new MenuItem("INT-MIMG Survive Pizza", "INT-MIMG-SURVIVE-PIZZA", new BigDecimal("9.00"), true));
        ModifierGroup group = modifierGroupRepository.save(new ModifierGroup("INT-MIMG Survive Toppings", 0, 3));
        MenuItemModifierGroupAssignment assignment = assignmentRepository.save(
                new MenuItemModifierGroupAssignment(item, group, 1));

        assignmentRepository.delete(assignment);

        assertThat(assignmentRepository.findByMenuItemIdOrderByDisplayOrder(item.getId())).isEmpty();
        assertThat(menuItemRepository.findById(item.getId())).isPresent();
        assertThat(modifierGroupRepository.findById(group.getId())).isPresent();

        deleteModifierGroupCascade(group.getId());
        menuItemRepository.deleteById(item.getId());
    }

    @Test
    @DisplayName("unassigning from one MenuItem does not affect the same group on another MenuItem")
    void unassignFromOneItemKeepsOtherAssignments() {
        MenuItem pizza = menuItemRepository.save(
                new MenuItem("INT-MIMG Unassign Pizza", "INT-MIMG-UNASSIGN-PIZZA", new BigDecimal("9.00"), true));
        MenuItem pasta = menuItemRepository.save(
                new MenuItem("INT-MIMG Unassign Pasta", "INT-MIMG-UNASSIGN-PASTA", new BigDecimal("8.00"), true));
        ModifierGroup toppings = modifierGroupRepository.save(new ModifierGroup("INT-MIMG Unassign Toppings", 0, 3));

        MenuItemModifierGroupAssignment pizzaAssignment = assignmentRepository.save(
                new MenuItemModifierGroupAssignment(pizza, toppings, 1));
        assignmentRepository.save(new MenuItemModifierGroupAssignment(pasta, toppings, 1));

        assignmentRepository.delete(pizzaAssignment);

        assertThat(assignmentRepository.findByMenuItemIdOrderByDisplayOrder(pizza.getId())).isEmpty();
        assertThat(assignmentRepository.findByMenuItemIdOrderByDisplayOrder(pasta.getId())).hasSize(1);

        deleteMenuItemModifierGroups(pizza.getId());
        deleteMenuItemModifierGroups(pasta.getId());
        deleteModifierGroupCascade(toppings.getId());
        menuItemRepository.deleteById(pizza.getId());
        menuItemRepository.deleteById(pasta.getId());
    }

    // --- helpers ---

    /**
     * Delete every modifier group assignment attached to a menu item.
     * Used so the FK constraint from menu_item_modifier_group_assignment
     * does not block cleanup of the item or group.
     */
    private void deleteMenuItemModifierGroups(Long menuItemId) {
        jdbcTemplate.update(
                "DELETE FROM menu_item_modifier_group_assignment WHERE menu_item_id = ?", menuItemId);
    }

    /**
     * Delete every assignment attached to a modifier group (across all
     * aggregates this group participates in). Used so the ModifierGroup
     * row can be removed without tripping the FK from
     * menu_item_modifier_group_assignment.
     */
    private void deleteModifierGroupCascade(Long modifierGroupId) {
        jdbcTemplate.update(
                "DELETE FROM menu_item_modifier_group_assignment WHERE modifier_group_id = ?", modifierGroupId);
        jdbcTemplate.update(
                "DELETE FROM modifier_group_assignment WHERE modifier_group_id = ?", modifierGroupId);
        modifierGroupRepository.deleteById(modifierGroupId);
    }
}
