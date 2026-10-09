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
import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.entity.EmployeeGroupMembership;
import com.example.posapp.entity.Organization;

/**
 * Integration tests for the Employee ↔ EmployeeGroup membership layer
 * against a real PostgreSQL.
 * <p>
 * Flyway applies V14 to create {@code employee_group_membership} and add
 * the supporting {@code uk_employee_id_organization} unique on
 * {@code employee}; Hibernate {@code ddl-auto=validate} then confirms the
 * JPA model matches. These tests verify the schema metadata (columns,
 * named constraints, indexes, the two composite foreign keys), the
 * database-level guarantees (unique pair, cross-organization rejection via
 * the composite FKs, delete-blocking FKs on both sides), and the queries
 * the membership service relies on.
 * </p>
 */
@SpringBootTest
@Testcontainers
class EmployeeGroupMembershipRepositoryIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database; {@code @ServiceConnection} feeds
     * its JDBC URL and credentials into the Spring environment in place of
     * the configured localhost datasource.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private EmployeeGroupRepository employeeGroupRepository;

    @Autowired
    private EmployeeGroupMembershipRepository membershipRepository;

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

    private static EmployeeGroup group(Organization owner, String name) {
        EmployeeGroup group = new EmployeeGroup(name);
        group.setOrganization(owner);
        return group;
    }

    // --- schema metadata matches the JPA model ---

    @Test
    @DisplayName("employee_group_membership table matches the JPA model: id, organization_id, employee_id, employee_group_id")
    void membershipTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'employee_group_membership'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "organization_id", "employee_id", "employee_group_id");
        assertThat(columns)
                .filteredOn(column -> "YES".equals(column.get("is_nullable")))
                .isEmpty();
        assertThat(columns.get(1))
                .containsEntry("column_name", "organization_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(2))
                .containsEntry("column_name", "employee_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "NO");
        assertThat(columns.get(3))
                .containsEntry("column_name", "employee_group_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("employee_group_membership declares its named constraints: unique pair plus two composite FKs")
    void membershipConstraintsAreNamedAndPresent() {
        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(
                "SELECT constraint_name, constraint_type"
                + " FROM information_schema.table_constraints WHERE table_name = 'employee_group_membership'");

        assertThat(constraints)
                .extracting(row -> row.get("constraint_name") + ":" + row.get("constraint_type"))
                .contains(
                        "uk_employee_group_membership:UNIQUE",
                        "fk_egm_employee:FOREIGN KEY",
                        "fk_egm_employee_group:FOREIGN KEY");
        assertThat(constraints)
                .extracting(row -> row.get("constraint_type"))
                .contains("PRIMARY KEY");
    }

    @Test
    @DisplayName("fk_egm_employee is composite: (employee_id, organization_id) references employee (id, organization_id)")
    void employeeForeignKeyIsCompositeOverEmployeeAndOrganization() {
        List<String> localColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.key_column_usage"
                + " WHERE constraint_name = 'fk_egm_employee' ORDER BY ordinal_position",
                String.class);
        List<Map<String, Object>> referencedColumns = jdbcTemplate.queryForList(
                "SELECT table_name, column_name FROM information_schema.constraint_column_usage"
                + " WHERE constraint_name = 'fk_egm_employee'");

        assertThat(localColumns).containsExactly("employee_id", "organization_id");
        assertThat(referencedColumns)
                .extracting(row -> row.get("table_name") + ":" + row.get("column_name"))
                .containsExactlyInAnyOrder("employee:id", "employee:organization_id");
    }

    @Test
    @DisplayName("fk_egm_employee_group is composite: (employee_group_id, organization_id) references employee_group (id, organization_id)")
    void groupForeignKeyIsCompositeOverGroupAndOrganization() {
        List<String> localColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.key_column_usage"
                + " WHERE constraint_name = 'fk_egm_employee_group' ORDER BY ordinal_position",
                String.class);
        List<Map<String, Object>> referencedColumns = jdbcTemplate.queryForList(
                "SELECT table_name, column_name FROM information_schema.constraint_column_usage"
                + " WHERE constraint_name = 'fk_egm_employee_group'");

        assertThat(localColumns).containsExactly("employee_group_id", "organization_id");
        assertThat(referencedColumns)
                .extracting(row -> row.get("table_name") + ":" + row.get("column_name"))
                .containsExactlyInAnyOrder("employee_group:id", "employee_group:organization_id");
    }

    @Test
    @DisplayName("employee carries uk_employee_id_organization so the composite FK from membership is possible")
    void employeeSupportsCompositeForeignKeyTarget() {
        List<String> uniqueColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.key_column_usage"
                + " WHERE constraint_name = 'uk_employee_id_organization' ORDER BY ordinal_position",
                String.class);

        assertThat(uniqueColumns).containsExactly("id", "organization_id");
    }

    @Test
    @DisplayName("explicit index covers the group-side lookup column")
    void explicitIndexCoversGroupLookup() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'employee_group_membership'", String.class);

        assertThat(indexes).contains("idx_egm_employee_group_id");
    }

    // --- database-level enforcement ---

    @Test
    @DisplayName("uk_employee_group_membership rejects attaching the same employee to the same group twice")
    void duplicateMembershipIsRejectedByUniqueConstraint() {
        Organization owner = organizationRepository.save(org("INT-EGM-DUP Acme"));
        Employee employee = employeeRepository.save(employee(owner, "INT-EGM-DUP Employee"));
        EmployeeGroup group = employeeGroupRepository.save(group(owner, "INT-EGM-DUP Group"));
        membershipRepository.saveAndFlush(new EmployeeGroupMembership(employee, group));

        EmployeeGroupMembership duplicate = new EmployeeGroupMembership(employee, group);

        // The production detector in EmployeeGroupMembershipService walks
        // the entire cause chain looking for "uk_employee_group_membership";
        // asserting on the leaf cause (getMostSpecificCause()) pins the
        // strongest guarantee: PostgreSQL names the constraint on the
        // lowest-level SQLException, so walking any higher link in the
        // chain (Hibernate's ConstraintViolationException or Spring's
        // DuplicateKeyException wrapper) still surfaces the same substring.
        assertThatThrownBy(() -> membershipRepository.saveAndFlush(duplicate))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, ex ->
                        assertThat(ex.getMostSpecificCause().getMessage())
                                .contains("uk_employee_group_membership"));

        // Cleanup
        membershipRepository.deleteAll(membershipRepository.findByEmployeeId(employee.getId()));
        employeeRepository.deleteById(employee.getId());
        employeeGroupRepository.deleteById(group.getId());
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("the composite FK rejects a membership whose organization_id does not match the employee's organization")
    void tamperedOrganizationIdIsRejectedByCompositeForeignKey() {
        Organization acme = organizationRepository.save(org("INT-EGM-XORG Acme"));
        Organization globex = organizationRepository.save(org("INT-EGM-XORG Globex"));
        Employee acmeEmployee = employeeRepository.save(employee(acme, "INT-EGM-XORG Employee"));
        EmployeeGroup acmeGroup = employeeGroupRepository.save(group(acme, "INT-EGM-XORG Group"));
        EmployeeGroupMembership membership = membershipRepository.save(
                new EmployeeGroupMembership(acmeEmployee, acmeGroup));

        // Simulate a caller trying to bypass the service check: rewrite
        // the denormalized organization_id to another organization. The
        // composite FK (employee_id, organization_id) → employee
        // (id, organization_id) refuses the update because the acme
        // employee's row still says acme.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE employee_group_membership SET organization_id = ? WHERE id = ?",
                globex.getId(), membership.getId()))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, ex ->
                        assertThat(ex.getMostSpecificCause().getMessage())
                                .contains("fk_egm_employee"));

        // Cleanup
        membershipRepository.deleteById(membership.getId());
        employeeRepository.deleteById(acmeEmployee.getId());
        employeeGroupRepository.deleteById(acmeGroup.getId());
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("deleting an employee that still belongs to a group is blocked by the FK")
    void deletingEmployeeWithMembershipsIsBlocked() {
        Organization owner = organizationRepository.save(org("INT-EGM-EDEL Acme"));
        Employee employee = employeeRepository.save(employee(owner, "INT-EGM-EDEL Employee"));
        EmployeeGroup group = employeeGroupRepository.save(group(owner, "INT-EGM-EDEL Group"));
        EmployeeGroupMembership membership = membershipRepository.save(
                new EmployeeGroupMembership(employee, group));

        assertThatThrownBy(() -> employeeRepository.deleteById(employee.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: detach first so the employee can go away.
        membershipRepository.deleteById(membership.getId());
        employeeRepository.deleteById(employee.getId());
        employeeGroupRepository.deleteById(group.getId());
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("deleting a group that still has employee members is blocked by the FK")
    void deletingGroupWithMembersIsBlocked() {
        Organization owner = organizationRepository.save(org("INT-EGM-GDEL Acme"));
        Employee employee = employeeRepository.save(employee(owner, "INT-EGM-GDEL Employee"));
        EmployeeGroup group = employeeGroupRepository.save(group(owner, "INT-EGM-GDEL Group"));
        EmployeeGroupMembership membership = membershipRepository.save(
                new EmployeeGroupMembership(employee, group));

        assertThatThrownBy(() -> employeeGroupRepository.deleteById(group.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: detach first so the group can go away.
        membershipRepository.deleteById(membership.getId());
        employeeRepository.deleteById(employee.getId());
        employeeGroupRepository.deleteById(group.getId());
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("moving an employee that still has memberships to another organization is blocked by the composite FK")
    void movingEmployeeWithMembershipsToAnotherOrganizationIsBlocked() {
        Organization acme = organizationRepository.save(org("INT-EGM-EMOVE Acme"));
        Organization globex = organizationRepository.save(org("INT-EGM-EMOVE Globex"));
        Employee employee = employeeRepository.save(employee(acme, "INT-EGM-EMOVE Employee"));
        EmployeeGroup group = employeeGroupRepository.save(group(acme, "INT-EGM-EMOVE Group"));
        EmployeeGroupMembership membership = membershipRepository.save(
                new EmployeeGroupMembership(employee, group));

        // The service layer refuses this move up front, but the composite
        // FK is the DB-level backstop: rewriting employee.organization_id
        // while a membership row still points at (employee.id, acme.id)
        // violates fk_egm_employee because the pair no longer exists.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE employee SET organization_id = ? WHERE id = ?",
                globex.getId(), employee.getId()))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, ex ->
                        assertThat(ex.getMostSpecificCause().getMessage())
                                .contains("fk_egm_employee"));

        // Verify the employee is still attached to acme and the
        // membership is intact (the update rolled back cleanly).
        assertThat(jdbcTemplate.queryForObject(
                "SELECT organization_id FROM employee WHERE id = ?", Long.class,
                employee.getId()))
                .isEqualTo(acme.getId());
        assertThat(membershipRepository.countByEmployeeId(employee.getId())).isEqualTo(1L);

        // Cleanup: detach first so the employee can be moved and removed.
        membershipRepository.deleteById(membership.getId());
        employeeRepository.deleteById(employee.getId());
        employeeGroupRepository.deleteById(group.getId());
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("removing a membership leaves both the employee and the group intact")
    void removingMembershipDoesNotCascadeToAggregates() {
        Organization owner = organizationRepository.save(org("INT-EGM-UNASSIGN Acme"));
        Employee employee = employeeRepository.save(employee(owner, "INT-EGM-UNASSIGN Employee"));
        EmployeeGroup group = employeeGroupRepository.save(group(owner, "INT-EGM-UNASSIGN Group"));
        EmployeeGroupMembership membership = membershipRepository.save(
                new EmployeeGroupMembership(employee, group));

        membershipRepository.deleteById(membership.getId());
        membershipRepository.flush();

        assertThat(employeeRepository.findById(employee.getId())).isPresent();
        assertThat(employeeGroupRepository.findById(group.getId())).isPresent();

        // Cleanup
        employeeRepository.deleteById(employee.getId());
        employeeGroupRepository.deleteById(group.getId());
        organizationRepository.deleteById(owner.getId());
    }

    // --- queries used by the service layer ---

    @Test
    @DisplayName("existsByEmployeeIdAndEmployeeGroupId and findByEmployeeIdAndEmployeeGroupId reflect the pair")
    void pairLookupsReflectRows() {
        Organization acme = organizationRepository.save(org("INT-EGM-PAIR Acme"));
        Organization globex = organizationRepository.save(org("INT-EGM-PAIR Globex"));
        Employee acmeEmployee = employeeRepository.save(employee(acme, "INT-EGM-PAIR Acme Employee"));
        Employee globexEmployee = employeeRepository.save(employee(globex, "INT-EGM-PAIR Globex Employee"));
        EmployeeGroup acmeGroup = employeeGroupRepository.save(group(acme, "INT-EGM-PAIR Acme Group"));
        EmployeeGroup globexGroup = employeeGroupRepository.save(group(globex, "INT-EGM-PAIR Globex Group"));
        EmployeeGroupMembership attached = membershipRepository.save(
                new EmployeeGroupMembership(acmeEmployee, acmeGroup));

        assertThat(membershipRepository.existsByEmployeeIdAndEmployeeGroupId(
                acmeEmployee.getId(), acmeGroup.getId())).isTrue();
        assertThat(membershipRepository.existsByEmployeeIdAndEmployeeGroupId(
                globexEmployee.getId(), globexGroup.getId())).isFalse();
        assertThat(membershipRepository.findByEmployeeIdAndEmployeeGroupId(
                acmeEmployee.getId(), acmeGroup.getId()))
                .get()
                .extracting(EmployeeGroupMembership::getId)
                .isEqualTo(attached.getId());
        assertThat(membershipRepository.findByEmployeeIdAndEmployeeGroupId(
                globexEmployee.getId(), globexGroup.getId())).isEmpty();

        // Cleanup
        membershipRepository.deleteById(attached.getId());
        employeeRepository.deleteAll(List.of(acmeEmployee, globexEmployee));
        employeeGroupRepository.deleteAll(List.of(acmeGroup, globexGroup));
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("findByEmployeeId and findByEmployeeGroupId return every attached row on each side")
    void listLookupsReturnBothSides() {
        Organization owner = organizationRepository.save(org("INT-EGM-LIST Acme"));
        Employee first = employeeRepository.save(employee(owner, "INT-EGM-LIST First"));
        Employee second = employeeRepository.save(employee(owner, "INT-EGM-LIST Second"));
        EmployeeGroup frontOfHouse = employeeGroupRepository.save(group(owner, "INT-EGM-LIST Front"));
        EmployeeGroup baristas = employeeGroupRepository.save(group(owner, "INT-EGM-LIST Baristas"));
        EmployeeGroupMembership firstFront = membershipRepository.save(
                new EmployeeGroupMembership(first, frontOfHouse));
        EmployeeGroupMembership firstBar = membershipRepository.save(
                new EmployeeGroupMembership(first, baristas));
        EmployeeGroupMembership secondFront = membershipRepository.save(
                new EmployeeGroupMembership(second, frontOfHouse));

        assertThat(membershipRepository.findByEmployeeId(first.getId()))
                .extracting(EmployeeGroupMembership::getId)
                .containsExactlyInAnyOrder(firstFront.getId(), firstBar.getId());
        assertThat(membershipRepository.findByEmployeeGroupId(frontOfHouse.getId()))
                .extracting(EmployeeGroupMembership::getId)
                .containsExactlyInAnyOrder(firstFront.getId(), secondFront.getId());
        assertThat(membershipRepository.findByEmployeeGroupId(baristas.getId()))
                .extracting(EmployeeGroupMembership::getId)
                .containsExactly(firstBar.getId());

        // Cleanup
        membershipRepository.deleteAll(List.of(firstFront, firstBar, secondFront));
        employeeRepository.deleteAll(List.of(first, second));
        employeeGroupRepository.deleteAll(List.of(frontOfHouse, baristas));
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("countByEmployeeId and countByEmployeeGroupId back the delete guards")
    void countsBackTheDeleteGuards() {
        Organization owner = organizationRepository.save(org("INT-EGM-CNT Acme"));
        Employee attached = employeeRepository.save(employee(owner, "INT-EGM-CNT Attached"));
        Employee detached = employeeRepository.save(employee(owner, "INT-EGM-CNT Detached"));
        EmployeeGroup groupWithMember = employeeGroupRepository.save(group(owner, "INT-EGM-CNT Grouped"));
        EmployeeGroup emptyGroup = employeeGroupRepository.save(group(owner, "INT-EGM-CNT Empty"));
        EmployeeGroupMembership membership = membershipRepository.save(
                new EmployeeGroupMembership(attached, groupWithMember));

        assertThat(membershipRepository.countByEmployeeId(attached.getId())).isEqualTo(1L);
        assertThat(membershipRepository.countByEmployeeId(detached.getId())).isZero();
        assertThat(membershipRepository.countByEmployeeGroupId(groupWithMember.getId())).isEqualTo(1L);
        assertThat(membershipRepository.countByEmployeeGroupId(emptyGroup.getId())).isZero();

        // Cleanup
        membershipRepository.deleteById(membership.getId());
        employeeRepository.deleteAll(List.of(attached, detached));
        employeeGroupRepository.deleteAll(List.of(groupWithMember, emptyGroup));
        organizationRepository.deleteById(owner.getId());
    }
}
