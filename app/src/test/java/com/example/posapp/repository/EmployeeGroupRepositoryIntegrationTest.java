package com.example.posapp.repository;

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

import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.entity.Organization;

/**
 * Integration tests for the Employee Group foundation against a real
 * PostgreSQL.
 * <p>
 * Flyway applies V12 to create {@code employee_group}; Hibernate
 * {@code ddl-auto=validate} then confirms the JPA model matches. These
 * tests verify schema metadata (columns, named constraints, indexes, the
 * composite parent foreign key), the database-level guarantees (NOT NULL
 * columns, the self-parent CHECK constraint, the same-organization
 * composite FK, the delete-blocking foreign keys), and the hierarchy
 * queries used by the service layer.
 * </p>
 */
@SpringBootTest
@Testcontainers
class EmployeeGroupRepositoryIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds its
     * JDBC URL and credentials into the Spring environment in place of the
     * configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private EmployeeGroupRepository employeeGroupRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static Organization org(String name) {
        return new Organization(name);
    }

    private static EmployeeGroup group(Organization owner, String name) {
        EmployeeGroup group = new EmployeeGroup(name);
        group.setOrganization(owner);
        return group;
    }

    private static EmployeeGroup group(Organization owner, String name, EmployeeGroup parent) {
        EmployeeGroup group = group(owner, name);
        group.setParent(parent);
        return group;
    }

    // --- schema metadata matches the JPA model ---

    @Test
    @DisplayName("employee_group table matches the JPA model: hierarchy, name, active")
    void employeeGroupTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'employee_group'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "organization_id", "parent_employee_group_id", "name", "active");
        assertThat(columns)
                .filteredOn(column -> "YES".equals(column.get("is_nullable")))
                .extracting(column -> column.get("column_name"))
                .containsExactly("parent_employee_group_id");
        assertThat(columns.get(1))
                .containsEntry("column_name", "organization_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(3))
                .containsEntry("column_name", "name")
                .containsEntry("data_type", "character varying")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(4))
                .containsEntry("column_name", "active")
                .containsEntry("data_type", "boolean")
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("employee_group declares its named constraints: two FKs, unique (id, organization_id), self-parent CHECK")
    void employeeGroupConstraintsAreNamedAndPresent() {
        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(
                "SELECT constraint_name, constraint_type"
                + " FROM information_schema.table_constraints WHERE table_name = 'employee_group'");

        assertThat(constraints)
                .extracting(row -> row.get("constraint_name") + ":" + row.get("constraint_type"))
                .contains(
                        "fk_employee_group_organization:FOREIGN KEY",
                        "fk_employee_group_parent:FOREIGN KEY",
                        "uk_employee_group_id_organization:UNIQUE",
                        "chk_employee_group_not_self_parent:CHECK");
        assertThat(constraints)
                .extracting(row -> row.get("constraint_type"))
                .contains("PRIMARY KEY");
    }

    @Test
    @DisplayName("employee_group.organization_id foreign key references organization(id) via fk_employee_group_organization")
    void employeeGroupForeignKeyPointsAtOrganization() {
        List<Map<String, Object>> fks = jdbcTemplate.queryForList(
                "SELECT tc.constraint_name, kcu.column_name, ccu.table_name AS referenced_table"
                + " FROM information_schema.table_constraints tc"
                + " JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name"
                + " JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name"
                + " WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_name = 'employee_group'"
                + " AND tc.constraint_name = 'fk_employee_group_organization'");

        assertThat(fks)
                .anySatisfy(row -> assertThat(row)
                        .containsEntry("constraint_name", "fk_employee_group_organization")
                        .containsEntry("column_name", "organization_id")
                        .containsEntry("referenced_table", "organization"));
    }

    @Test
    @DisplayName("fk_employee_group_parent is composite: (parent_employee_group_id, organization_id) references employee_group (id, organization_id)")
    void parentForeignKeyIsCompositeOverParentAndOrganization() {
        List<String> localColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.key_column_usage"
                + " WHERE constraint_name = 'fk_employee_group_parent' ORDER BY ordinal_position",
                String.class);
        List<Map<String, Object>> referencedColumns = jdbcTemplate.queryForList(
                "SELECT table_name, column_name FROM information_schema.constraint_column_usage"
                + " WHERE constraint_name = 'fk_employee_group_parent'");

        assertThat(localColumns).containsExactly("parent_employee_group_id", "organization_id");
        assertThat(referencedColumns)
                .extracting(row -> row.get("table_name") + ":" + row.get("column_name"))
                .containsExactlyInAnyOrder("employee_group:id", "employee_group:organization_id");
    }

    @Test
    @DisplayName("explicit indexes cover the organization and parent lookup columns")
    void explicitIndexesCoverLookupColumns() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'employee_group'", String.class);

        assertThat(indexes).contains(
                "idx_employee_group_organization_id",
                "idx_employee_group_parent_id");
    }

    // --- database-level enforcement ---

    @Test
    @DisplayName("a group cannot become its own parent: chk_employee_group_not_self_parent rejects the update")
    void selfParentIsRejectedByCheckConstraint() {
        Organization owner = organizationRepository.save(org("INT-EG-SELF Acme"));
        EmployeeGroup root = employeeGroupRepository.save(group(owner, "INT-EG-SELF Root"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE employee_group SET parent_employee_group_id = id WHERE id = ?", root.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_employee_group_not_self_parent");

        // Cleanup
        employeeGroupRepository.deleteById(root.getId());
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("a group cannot be parented across organizations: the composite FK rejects it")
    void crossOrganizationParentIsRejectedByCompositeForeignKey() {
        Organization acme = organizationRepository.save(org("INT-EG-XORG Acme"));
        Organization globex = organizationRepository.save(org("INT-EG-XORG Globex"));
        EmployeeGroup acmeRoot = employeeGroupRepository.save(group(acme, "INT-EG-XORG Acme Root"));
        EmployeeGroup globexRoot = employeeGroupRepository.save(group(globex, "INT-EG-XORG Globex Root"));

        globexRoot.setParent(acmeRoot);

        assertThatThrownBy(() -> employeeGroupRepository.saveAndFlush(globexRoot))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_employee_group_parent");

        // Cleanup
        employeeGroupRepository.deleteById(globexRoot.getId());
        employeeGroupRepository.deleteById(acmeRoot.getId());
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("required columns reject nulls: organization_id and name are NOT NULL")
    void requiredColumnsRejectNulls() {
        Organization owner = organizationRepository.save(org("INT-EG-NULL Acme"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO employee_group (organization_id, name) VALUES (NULL, 'INT-EG-NULL Orphan')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO employee_group (organization_id, name) VALUES (?, NULL)", owner.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("group names are NOT unique: two groups may share a name inside one organization")
    void groupNamesCanCollide() {
        Organization owner = organizationRepository.save(org("INT-EG-NAME Acme"));
        EmployeeGroup first = employeeGroupRepository.save(group(owner, "INT-EG-NAME Lead"));
        EmployeeGroup second = employeeGroupRepository.save(group(owner, "INT-EG-NAME Lead"));

        assertThat(first.getId()).isNotEqualTo(second.getId());

        // Cleanup
        employeeGroupRepository.deleteAll(List.of(first, second));
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("deleting an organization that still owns employee groups is blocked by the FK")
    void deletingOrganizationWithGroupsIsBlocked() {
        Organization owner = organizationRepository.save(org("INT-EG-DEL Acme"));
        EmployeeGroup root = employeeGroupRepository.save(group(owner, "INT-EG-DEL Root"));

        assertThatThrownBy(() -> organizationRepository.deleteById(owner.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: drop the group first so the organization can go away.
        employeeGroupRepository.deleteById(root.getId());
        organizationRepository.deleteById(owner.getId());
        assertThat(organizationRepository.findById(owner.getId())).isEmpty();
    }

    @Test
    @DisplayName("deleting a group that still has child groups is blocked by the FK")
    void deletingParentWithChildrenIsBlocked() {
        Organization owner = organizationRepository.save(org("INT-EG-DEL2 Acme"));
        EmployeeGroup parent = employeeGroupRepository.save(group(owner, "INT-EG-DEL2 Parent"));
        EmployeeGroup child = employeeGroupRepository.save(group(owner, "INT-EG-DEL2 Child", parent));

        assertThatThrownBy(() -> employeeGroupRepository.deleteById(parent.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: drop the child first so the parent can go away.
        employeeGroupRepository.deleteById(child.getId());
        employeeGroupRepository.deleteById(parent.getId());
        organizationRepository.deleteById(owner.getId());
    }

    // --- hierarchy queries used by the service layer ---

    @Test
    @DisplayName("findByParentId returns only the direct children, not deeper descendants")
    void findByParentIdReturnsDirectChildrenOnly() {
        Organization owner = organizationRepository.save(org("INT-EG-CHILD Acme"));
        EmployeeGroup root = employeeGroupRepository.save(group(owner, "INT-EG-CHILD Root"));
        EmployeeGroup first = employeeGroupRepository.save(group(owner, "INT-EG-CHILD First", root));
        EmployeeGroup second = employeeGroupRepository.save(group(owner, "INT-EG-CHILD Second", root));
        EmployeeGroup grandchild = employeeGroupRepository.save(
                group(owner, "INT-EG-CHILD Grandchild", first));

        List<EmployeeGroup> children = employeeGroupRepository.findByParentId(root.getId());

        assertThat(children)
                .extracting(EmployeeGroup::getName)
                .containsExactlyInAnyOrder("INT-EG-CHILD First", "INT-EG-CHILD Second");

        // Cleanup deepest-first so the FKs never block a delete.
        employeeGroupRepository.deleteById(grandchild.getId());
        employeeGroupRepository.deleteById(first.getId());
        employeeGroupRepository.deleteById(second.getId());
        employeeGroupRepository.deleteById(root.getId());
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("findByOrganizationIdAndParentIsNull returns the root groups of one organization")
    void findByOrganizationIdAndParentIsNullReturnsRootsOnly() {
        Organization acme = organizationRepository.save(org("INT-EG-ROOT Acme"));
        Organization globex = organizationRepository.save(org("INT-EG-ROOT Globex"));
        EmployeeGroup acmeRoot = employeeGroupRepository.save(group(acme, "INT-EG-ROOT Acme Root"));
        EmployeeGroup acmeChild = employeeGroupRepository.save(
                group(acme, "INT-EG-ROOT Acme Child", acmeRoot));
        EmployeeGroup globexRoot = employeeGroupRepository.save(group(globex, "INT-EG-ROOT Globex Root"));

        assertThat(employeeGroupRepository.findByOrganizationIdAndParentIsNull(acme.getId()))
                .extracting(EmployeeGroup::getName)
                .containsExactly("INT-EG-ROOT Acme Root");
        assertThat(employeeGroupRepository.findByOrganizationIdAndParentIsNull(globex.getId()))
                .extracting(EmployeeGroup::getName)
                .containsExactly("INT-EG-ROOT Globex Root");

        // Cleanup
        employeeGroupRepository.deleteById(acmeChild.getId());
        employeeGroupRepository.deleteById(acmeRoot.getId());
        employeeGroupRepository.deleteById(globexRoot.getId());
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("countByParentId and countByOrganizationId reflect the current hierarchy")
    void countsReflectTheCurrentHierarchy() {
        Organization acme = organizationRepository.save(org("INT-EG-CNT Acme"));
        Organization globex = organizationRepository.save(org("INT-EG-CNT Globex"));
        EmployeeGroup first = employeeGroupRepository.save(group(acme, "INT-EG-CNT First"));
        EmployeeGroup second = employeeGroupRepository.save(group(acme, "INT-EG-CNT Second"));
        EmployeeGroup child = employeeGroupRepository.save(group(acme, "INT-EG-CNT Child", first));
        EmployeeGroup foreignRoot = employeeGroupRepository.save(group(globex, "INT-EG-CNT Foreign"));

        assertThat(employeeGroupRepository.countByParentId(first.getId())).isEqualTo(1L);
        assertThat(employeeGroupRepository.countByParentId(second.getId())).isZero();
        assertThat(employeeGroupRepository.countByOrganizationId(acme.getId())).isEqualTo(3L);
        assertThat(employeeGroupRepository.countByOrganizationId(globex.getId())).isEqualTo(1L);

        // Cleanup
        employeeGroupRepository.deleteById(child.getId());
        employeeGroupRepository.deleteById(first.getId());
        employeeGroupRepository.deleteById(second.getId());
        employeeGroupRepository.deleteById(foreignRoot.getId());
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("a group can be re-parented within its organization")
    void groupCanBeReparentedWithinOrganization() {
        Organization owner = organizationRepository.save(org("INT-EG-REPA Acme"));
        EmployeeGroup first = employeeGroupRepository.save(group(owner, "INT-EG-REPA First"));
        EmployeeGroup second = employeeGroupRepository.save(group(owner, "INT-EG-REPA Second"));
        EmployeeGroup child = employeeGroupRepository.save(group(owner, "INT-EG-REPA Child", first));

        child.setParent(second);
        employeeGroupRepository.saveAndFlush(child);

        EmployeeGroup reloaded = employeeGroupRepository.findById(child.getId()).orElseThrow();
        assertThat(reloaded.getParent().getId()).isEqualTo(second.getId());
        assertThat(employeeGroupRepository.findByParentId(first.getId())).isEmpty();
        assertThat(employeeGroupRepository.findByParentId(second.getId()))
                .extracting(EmployeeGroup::getId)
                .containsExactly(child.getId());

        // Cleanup
        employeeGroupRepository.deleteById(child.getId());
        employeeGroupRepository.deleteById(first.getId());
        employeeGroupRepository.deleteById(second.getId());
        organizationRepository.deleteById(owner.getId());
    }
}
