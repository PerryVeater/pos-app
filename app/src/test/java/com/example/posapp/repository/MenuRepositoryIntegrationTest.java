package com.example.posapp.repository;

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

import com.example.posapp.entity.Menu;
import com.example.posapp.entity.MenuGroup;
import com.example.posapp.entity.MenuGroupAssignment;

/**
 * Integration tests for the menu / menu-group persistence stack against a
 * real PostgreSQL.
 * <p>
 * Flyway applies V7 to create {@code menu}, {@code menu_group}, and
 * {@code menu_group_assignment}; Hibernate {@code ddl-auto=validate} then
 * confirms the JPA model matches. These tests check schema metadata,
 * constraint enforcement, and the reusable-group semantics required by the
 * domain (the same {@link MenuGroup} can appear in multiple {@link Menu}s,
 * but a given (menu, group) pair is unique).
 * </p>
 * <p>
 * Cleanup uses {@code jdbcTemplate} deletes so each test starts and ends
 * without the JPA cascade or FK checks leaving residue behind; no test
 * relies on the DataLoader seed.
 * </p>
 */
@SpringBootTest
@Testcontainers
class MenuRepositoryIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds its
     * JDBC URL and credentials into the Spring environment in place of the
     * configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private MenuGroupRepository menuGroupRepository;

    @Autowired
    private MenuGroupAssignmentRepository assignmentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // --- schema metadata matches the JPA model ---

    @Test
    @DisplayName("menu table matches the JPA model: id, name, active")
    void menuTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'menu'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "name", "active");
        assertThat(columns.get(0))
                .containsEntry("column_name", "id")
                .containsEntry("data_type", "bigint");
        assertThat(columns.get(1))
                .containsEntry("column_name", "name")
                .containsEntry("data_type", "character varying")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(2))
                .containsEntry("column_name", "active")
                .containsEntry("data_type", "boolean")
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("menu_group table matches the JPA model: id, name, active")
    void menuGroupTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'menu_group'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "name", "active");
        assertThat(columns.get(1))
                .containsEntry("column_name", "name")
                .containsEntry("data_type", "character varying")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(2))
                .containsEntry("column_name", "active")
                .containsEntry("data_type", "boolean")
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("menu_group_assignment table matches the JPA model: id, menu_id, menu_group_id, display_order")
    void assignmentTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'menu_group_assignment'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "menu_id", "menu_group_id", "display_order");
        assertThat(columns.get(3))
                .containsEntry("column_name", "display_order")
                .containsEntry("data_type", "integer")
                .containsEntry("is_nullable", "NO");
    }

    // --- unique constraints ---

    @Test
    @DisplayName("menu.name is unique: PostgreSQL rejects a duplicate")
    void menuNameIsEnforcedUnique() {
        Menu first = menuRepository.save(new Menu("Integration Lunch"));
        assertThatThrownBy(() -> menuRepository.save(new Menu("Integration Lunch")))
                .isInstanceOf(DataIntegrityViolationException.class);

        deleteMenuCascade(first.getId());
    }

    @Test
    @DisplayName("menu_group.name is unique: PostgreSQL rejects a duplicate")
    void menuGroupNameIsEnforcedUnique() {
        MenuGroup first = menuGroupRepository.save(new MenuGroup("Integration Appetizers"));
        assertThatThrownBy(() -> menuGroupRepository.save(new MenuGroup("Integration Appetizers")))
                .isInstanceOf(DataIntegrityViolationException.class);

        menuGroupRepository.deleteById(first.getId());
    }

    @Test
    @DisplayName("menu_group_assignment (menu_id, menu_group_id) is unique")
    void assignmentPairIsEnforcedUnique() {
        Menu menu = menuRepository.save(new Menu("Integration Pair Uniqueness"));
        MenuGroup group = menuGroupRepository.save(new MenuGroup("Integration Pair Group"));
        assignmentRepository.save(new MenuGroupAssignment(menu, group, 1));

        assertThatThrownBy(() -> assignmentRepository.save(new MenuGroupAssignment(menu, group, 2)))
                .isInstanceOf(DataIntegrityViolationException.class);

        deleteMenuCascade(menu.getId());
        menuGroupRepository.deleteById(group.getId());
    }

    // --- foreign keys ---

    @Test
    @DisplayName("menu_group_assignment exposes the two expected foreign keys")
    void assignmentForeignKeysExist() {
        List<Map<String, Object>> foreignKeys = jdbcTemplate.queryForList(
                "SELECT tc.constraint_name, ccu.table_name AS referenced_table"
                + " FROM information_schema.table_constraints tc"
                + " JOIN information_schema.constraint_column_usage ccu"
                + "   ON tc.constraint_name = ccu.constraint_name"
                + "  AND tc.constraint_schema = ccu.constraint_schema"
                + " WHERE tc.table_schema = 'public' AND tc.table_name = 'menu_group_assignment'"
                + "   AND tc.constraint_type = 'FOREIGN KEY'"
                + " ORDER BY tc.constraint_name");

        assertThat(foreignKeys).hasSize(2);
        assertThat(foreignKeys)
                .extracting(row -> (String) row.get("constraint_name"))
                .containsExactly("fk_menu_group_assignment_group", "fk_menu_group_assignment_menu");
        assertThat(foreignKeys)
                .extracting(row -> (String) row.get("referenced_table"))
                .containsExactlyInAnyOrder("menu", "menu_group");
    }

    @Test
    @DisplayName("a menu_group still referenced by an assignment cannot be deleted")
    void assignmentBlocksMenuGroupDeletion() {
        Menu menu = menuRepository.save(new Menu("Integration Blocker"));
        MenuGroup group = menuGroupRepository.save(new MenuGroup("Integration Blocker Group"));
        assignmentRepository.save(new MenuGroupAssignment(menu, group, 1));

        assertThatThrownBy(() -> menuGroupRepository.deleteById(group.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        deleteMenuCascade(menu.getId());
        menuGroupRepository.deleteById(group.getId());
    }

    // --- reusability and display order ---

    @Test
    @DisplayName("menu group is reusable: the same group can be assigned to multiple menus")
    void menuGroupIsReusableAcrossMenus() {
        Menu lunch = menuRepository.save(new Menu("Integration Reuse Lunch"));
        Menu dinner = menuRepository.save(new Menu("Integration Reuse Dinner"));
        MenuGroup apps = menuGroupRepository.save(new MenuGroup("Integration Reuse Appetizers"));

        MenuGroupAssignment lunchAssignment = assignmentRepository.save(
                new MenuGroupAssignment(lunch, apps, 1));
        MenuGroupAssignment dinnerAssignment = assignmentRepository.save(
                new MenuGroupAssignment(dinner, apps, 2));

        assertThat(assignmentRepository.findByMenuIdOrderByDisplayOrder(lunch.getId()))
                .extracting(assignment -> assignment.getMenuGroup().getId())
                .containsExactly(apps.getId());
        assertThat(assignmentRepository.findByMenuIdOrderByDisplayOrder(dinner.getId()))
                .extracting(assignment -> assignment.getMenuGroup().getId())
                .containsExactly(apps.getId());
        assertThat(lunchAssignment.getDisplayOrder()).isEqualTo(1);
        assertThat(dinnerAssignment.getDisplayOrder()).isEqualTo(2);

        deleteMenuCascade(lunch.getId());
        deleteMenuCascade(dinner.getId());
        menuGroupRepository.deleteById(apps.getId());
    }

    @Test
    @DisplayName("assignments are returned in display order regardless of insertion order")
    void assignmentsAreReturnedInDisplayOrder() {
        Menu menu = menuRepository.save(new Menu("Integration Order"));
        MenuGroup first = menuGroupRepository.save(new MenuGroup("Integration Order A"));
        MenuGroup second = menuGroupRepository.save(new MenuGroup("Integration Order B"));
        MenuGroup third = menuGroupRepository.save(new MenuGroup("Integration Order C"));

        assignmentRepository.save(new MenuGroupAssignment(menu, second, 5));
        assignmentRepository.save(new MenuGroupAssignment(menu, third, 7));
        assignmentRepository.save(new MenuGroupAssignment(menu, first, 1));

        assertThat(assignmentRepository.findByMenuIdOrderByDisplayOrder(menu.getId()))
                .extracting(assignment -> assignment.getMenuGroup().getName())
                .containsExactly(
                        "Integration Order A",
                        "Integration Order B",
                        "Integration Order C");

        deleteMenuCascade(menu.getId());
        menuGroupRepository.deleteById(first.getId());
        menuGroupRepository.deleteById(second.getId());
        menuGroupRepository.deleteById(third.getId());
    }

    @Test
    @DisplayName("unassigning one menu does not affect the same group's assignment on another menu")
    void unassignFromOneMenuKeepsOtherAssignments() {
        Menu lunch = menuRepository.save(new Menu("Integration Unassign Lunch"));
        Menu dinner = menuRepository.save(new Menu("Integration Unassign Dinner"));
        MenuGroup apps = menuGroupRepository.save(new MenuGroup("Integration Unassign Group"));

        MenuGroupAssignment lunchAssignment = assignmentRepository.save(
                new MenuGroupAssignment(lunch, apps, 1));
        assignmentRepository.save(new MenuGroupAssignment(dinner, apps, 1));

        assignmentRepository.delete(lunchAssignment);

        assertThat(assignmentRepository.findByMenuIdOrderByDisplayOrder(lunch.getId())).isEmpty();
        assertThat(assignmentRepository.findByMenuIdOrderByDisplayOrder(dinner.getId())).hasSize(1);

        deleteMenuCascade(dinner.getId());
        menuRepository.deleteById(lunch.getId());
        menuGroupRepository.deleteById(apps.getId());
    }

    @Test
    @DisplayName("menuRepository exposes name lookup and duplicate-detection helpers")
    void menuRepositoryLookupHelpers() {
        Menu saved = menuRepository.save(new Menu("Integration Lookup Lunch"));

        Optional<Menu> byName = menuRepository.findByName("Integration Lookup Lunch");
        assertThat(byName).isPresent();
        assertThat(menuRepository.existsByName("Integration Lookup Lunch")).isTrue();
        assertThat(menuRepository.existsByNameAndIdNot("Integration Lookup Lunch", saved.getId()))
                .isFalse();

        deleteMenuCascade(saved.getId());
    }

    /**
     * Delete every assignment attached to the menu, then the menu itself.
     * Used by tests that create assignments so the FK constraint does not
     * block cleanup.
     */
    private void deleteMenuCascade(Long menuId) {
        jdbcTemplate.update("DELETE FROM menu_group_assignment WHERE menu_id = ?", menuId);
        menuRepository.deleteById(menuId);
    }
}
