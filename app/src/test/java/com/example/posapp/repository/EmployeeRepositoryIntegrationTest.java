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

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Organization;

/**
 * Integration tests for the Employee foundation against a real
 * PostgreSQL.
 * <p>
 * Flyway applies V13 to create {@code employee}; Hibernate
 * {@code ddl-auto=validate} then confirms the JPA model matches. These
 * tests verify schema metadata (columns, named constraints, indexes), the
 * database-level guarantees (NOT NULL columns, the organization foreign
 * key, the organization-scoped email uniqueness), the delete-blocking
 * foreign key, and the queries used by the service layer.
 * </p>
 */
@SpringBootTest
@Testcontainers
class EmployeeRepositoryIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds its
     * JDBC URL and credentials into the Spring environment in place of the
     * configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static Organization org(String name) {
        return new Organization(name);
    }

    private static Employee employee(Organization owner, String name) {
        Employee employee = new Employee(name);
        employee.setOrganization(owner);
        return employee;
    }

    private static Employee employee(Organization owner, String name, String email) {
        Employee employee = employee(owner, name);
        employee.setEmail(email);
        return employee;
    }

    // --- schema metadata matches the JPA model ---

    @Test
    @DisplayName("employee table matches the JPA model: organization, name, email, active")
    void employeeTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'employee'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "organization_id", "name", "email", "active");
        assertThat(columns)
                .filteredOn(column -> "YES".equals(column.get("is_nullable")))
                .extracting(column -> column.get("column_name"))
                .containsExactly("email");
        assertThat(columns.get(1))
                .containsEntry("column_name", "organization_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(2))
                .containsEntry("column_name", "name")
                .containsEntry("data_type", "character varying")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(3))
                .containsEntry("column_name", "email")
                .containsEntry("data_type", "character varying")
                .containsEntry("is_nullable", "YES");
        assertThat(columns.get(4))
                .containsEntry("column_name", "active")
                .containsEntry("data_type", "boolean")
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("employee declares its named constraints: organization FK and organization-scoped email uniqueness")
    void employeeConstraintsAreNamedAndPresent() {
        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(
                "SELECT constraint_name, constraint_type"
                + " FROM information_schema.table_constraints WHERE table_name = 'employee'");

        assertThat(constraints)
                .extracting(row -> row.get("constraint_name") + ":" + row.get("constraint_type"))
                .contains(
                        "fk_employee_organization:FOREIGN KEY",
                        "uk_employee_organization_email:UNIQUE");
        assertThat(constraints)
                .extracting(row -> row.get("constraint_type"))
                .contains("PRIMARY KEY");
    }

    @Test
    @DisplayName("employee.organization_id references organization(id) via fk_employee_organization")
    void organizationForeignKeyPointsAtOrganization() {
        List<Map<String, Object>> fks = jdbcTemplate.queryForList(
                "SELECT tc.constraint_name, kcu.column_name, ccu.table_name AS referenced_table"
                + " FROM information_schema.table_constraints tc"
                + " JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name"
                + " JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name"
                + " WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_name = 'employee'"
                + " AND tc.constraint_name = 'fk_employee_organization'");

        assertThat(fks)
                .anySatisfy(row -> assertThat(row)
                        .containsEntry("constraint_name", "fk_employee_organization")
                        .containsEntry("column_name", "organization_id")
                        .containsEntry("referenced_table", "organization"));
    }

    @Test
    @DisplayName("uk_employee_organization_email covers (organization_id, email)")
    void uniqueConstraintCoversOrganizationAndEmail() {
        List<String> uniqueColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.key_column_usage"
                + " WHERE constraint_name = 'uk_employee_organization_email'"
                + " ORDER BY ordinal_position",
                String.class);

        assertThat(uniqueColumns).containsExactly("organization_id", "email");
    }

    @Test
    @DisplayName("explicit index covers the organization_id lookup column")
    void explicitIndexesCoverLookupColumns() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'employee'", String.class);

        assertThat(indexes).contains("idx_employee_organization_id");
    }

    // --- database-level enforcement ---

    @Test
    @DisplayName("required columns reject nulls: organization_id, name, and active are NOT NULL")
    void requiredColumnsRejectNulls() {
        Organization owner = organizationRepository.save(org("INT-EMP-NULL Acme"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO employee (organization_id, name) VALUES (NULL, 'INT-EMP-NULL Orphan')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO employee (organization_id, name) VALUES (?, NULL)", owner.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO employee (organization_id, name, active) VALUES (?, 'INT-EMP-NULL Active', NULL)",
                owner.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("an email is unique within an organization when present: uk_employee_organization_email rejects the duplicate")
    void emailIsUniqueWithinOrganizationWhenPresent() {
        Organization owner = organizationRepository.save(org("INT-EMP-DUP Acme"));
        Employee first = employeeRepository.save(
                employee(owner, "INT-EMP-DUP First", "dup@example.com"));

        Employee second = employee(owner, "INT-EMP-DUP Second", "dup@example.com");

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_employee_organization_email");

        // Cleanup
        employeeRepository.deleteById(first.getId());
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("the same email is allowed in different organizations")
    void sameEmailIsAllowedInDifferentOrganizations() {
        Organization acme = organizationRepository.save(org("INT-EMP-SHARED Acme"));
        Organization globex = organizationRepository.save(org("INT-EMP-SHARED Globex"));
        Employee acmeEmployee = employeeRepository.save(
                employee(acme, "INT-EMP-SHARED Acme Employee", "shared@example.com"));
        Employee globexEmployee = employeeRepository.save(
                employee(globex, "INT-EMP-SHARED Globex Employee", "shared@example.com"));

        assertThat(globexEmployee.getId()).isNotEqualTo(acmeEmployee.getId());
        assertThat(globexEmployee.getEmail()).isEqualTo("shared@example.com");

        // Cleanup
        employeeRepository.deleteAll(List.of(acmeEmployee, globexEmployee));
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("multiple employees without an email are allowed: NULLs are distinct in the unique constraint")
    void multipleEmployeesWithoutEmailAreAllowed() {
        Organization owner = organizationRepository.save(org("INT-EMP-NOEMAIL Acme"));
        Employee first = employeeRepository.save(employee(owner, "INT-EMP-NOEMAIL First"));
        Employee second = employeeRepository.save(employee(owner, "INT-EMP-NOEMAIL Second"));

        assertThat(first.getEmail()).isNull();
        assertThat(second.getEmail()).isNull();
        assertThat(second.getId()).isNotEqualTo(first.getId());

        // Cleanup
        employeeRepository.deleteAll(List.of(first, second));
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("emails are stored as submitted: the uniqueness check is case-sensitive")
    void emailsAreStoredAsSubmitted() {
        Organization owner = organizationRepository.save(org("INT-EMP-CASE Acme"));
        Employee upper = employeeRepository.save(
                employee(owner, "INT-EMP-CASE Upper", "Ada@Example.com"));
        Employee lower = employeeRepository.save(
                employee(owner, "INT-EMP-CASE Lower", "ada@example.com"));

        assertThat(upper.getEmail()).isEqualTo("Ada@Example.com");
        assertThat(lower.getId()).isNotEqualTo(upper.getId());

        // Cleanup
        employeeRepository.deleteAll(List.of(upper, lower));
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("employee names are NOT unique: two employees may share a name inside one organization")
    void employeeNamesCanCollide() {
        Organization owner = organizationRepository.save(org("INT-EMP-NAME Acme"));
        Employee first = employeeRepository.save(employee(owner, "INT-EMP-NAME Lead"));
        Employee second = employeeRepository.save(employee(owner, "INT-EMP-NAME Lead"));

        assertThat(first.getId()).isNotEqualTo(second.getId());

        // Cleanup
        employeeRepository.deleteAll(List.of(first, second));
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("deleting an organization that still owns employees is blocked by the FK")
    void deletingOrganizationWithEmployeesIsBlocked() {
        Organization owner = organizationRepository.save(org("INT-EMP-DEL Acme"));
        Employee employee = employeeRepository.save(employee(owner, "INT-EMP-DEL Employee"));

        assertThatThrownBy(() -> organizationRepository.deleteById(owner.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: drop the employee first so the organization can go away.
        employeeRepository.deleteById(employee.getId());
        organizationRepository.deleteById(owner.getId());
        assertThat(organizationRepository.findById(owner.getId())).isEmpty();
    }

    // --- queries used by the service layer ---

    @Test
    @DisplayName("existsByOrganizationIdAndEmail is scoped to the organization and case-sensitive")
    void existsByOrganizationIdAndEmailReflectsRows() {
        Organization acme = organizationRepository.save(org("INT-EMP-EXISTS Acme"));
        Organization globex = organizationRepository.save(org("INT-EMP-EXISTS Globex"));
        Employee acmeEmployee = employeeRepository.save(
                employee(acme, "INT-EMP-EXISTS Acme Employee", "acme@example.com"));
        Employee globexEmployee = employeeRepository.save(
                employee(globex, "INT-EMP-EXISTS Globex Employee", "globex@example.com"));

        assertThat(employeeRepository.existsByOrganizationIdAndEmail(
                acme.getId(), "acme@example.com")).isTrue();
        assertThat(employeeRepository.existsByOrganizationIdAndEmail(
                acme.getId(), "globex@example.com")).isFalse();
        assertThat(employeeRepository.existsByOrganizationIdAndEmail(
                globex.getId(), "acme@example.com")).isFalse();
        assertThat(employeeRepository.existsByOrganizationIdAndEmail(
                acme.getId(), "ACME@example.com")).isFalse();

        // Cleanup
        employeeRepository.deleteAll(List.of(acmeEmployee, globexEmployee));
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("existsByOrganizationIdAndEmailAndIdNot excludes the employee itself")
    void existsByOrganizationIdAndEmailAndIdNotExcludesTheEmployeeItself() {
        Organization owner = organizationRepository.save(org("INT-EMP-EXID Acme"));
        Employee first = employeeRepository.save(
                employee(owner, "INT-EMP-EXID First", "first@example.com"));
        Employee second = employeeRepository.save(
                employee(owner, "INT-EMP-EXID Second", "second@example.com"));

        assertThat(employeeRepository.existsByOrganizationIdAndEmailAndIdNot(
                owner.getId(), "first@example.com", first.getId())).isFalse();
        assertThat(employeeRepository.existsByOrganizationIdAndEmailAndIdNot(
                owner.getId(), "first@example.com", second.getId())).isTrue();
        assertThat(employeeRepository.existsByOrganizationIdAndEmailAndIdNot(
                owner.getId(), "missing@example.com", first.getId())).isFalse();

        // Cleanup
        employeeRepository.deleteAll(List.of(first, second));
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("countByOrganizationId counts the employees of each organization")
    void countByOrganizationIdCountsEmployeesPerOrganization() {
        Organization acme = organizationRepository.save(org("INT-EMP-CNT Acme"));
        Organization globex = organizationRepository.save(org("INT-EMP-CNT Globex"));
        Employee first = employeeRepository.save(employee(acme, "INT-EMP-CNT First"));
        Employee second = employeeRepository.save(employee(acme, "INT-EMP-CNT Second"));
        Employee third = employeeRepository.save(employee(globex, "INT-EMP-CNT Third"));

        assertThat(employeeRepository.countByOrganizationId(acme.getId())).isEqualTo(2L);
        assertThat(employeeRepository.countByOrganizationId(globex.getId())).isEqualTo(1L);

        // Cleanup
        employeeRepository.deleteAll(List.of(first, second, third));
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("an employee can be moved to another organization")
    void employeeCanBeMovedToAnotherOrganization() {
        Organization acme = organizationRepository.save(org("INT-EMP-MOVE Acme"));
        Organization globex = organizationRepository.save(org("INT-EMP-MOVE Globex"));
        Employee transferred = employeeRepository.save(
                employee(acme, "INT-EMP-MOVE Employee", "move@example.com"));

        transferred.setOrganization(globex);
        employeeRepository.saveAndFlush(transferred);

        Employee reloaded = employeeRepository.findById(transferred.getId()).orElseThrow();
        assertThat(reloaded.getOrganization().getId()).isEqualTo(globex.getId());
        assertThat(employeeRepository.countByOrganizationId(acme.getId())).isZero();

        // Cleanup
        employeeRepository.deleteById(transferred.getId());
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }
}
