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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.EmployeeStoreAssignment;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;

/**
 * Integration tests for the Employee ↔ Store assignment layer against a
 * real PostgreSQL.
 * <p>
 * Flyway applies V15 to create {@code employee_store_assignment} and add
 * the supporting {@code uk_stores_id_organization} unique on
 * {@code stores} (the matching {@code uk_employee_id_organization} already
 * exists from V14); Hibernate {@code ddl-auto=validate} then confirms the
 * JPA model matches. These tests verify the schema metadata (columns,
 * named constraints, indexes, the two composite foreign keys), the
 * database-level guarantees (unique pair, cross-organization rejection via
 * the composite FKs, delete-blocking FKs on both sides, and blocking an
 * organization change on either side while an assignment exists), and the
 * queries the assignment service relies on.
 * </p>
 */
@SpringBootTest
@Testcontainers
class EmployeeStoreAssignmentRepositoryIntegrationTest {

    /**
     * A page large enough for every assignment the tests create, so a paged
     * lookup can be asserted as one list.
     */
    private static final Pageable ONE_PAGE = PageRequest.of(0, 100);

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
    private StoreRepository storeRepository;

    @Autowired
    private EmployeeStoreAssignmentRepository assignmentRepository;

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

    private static Store store(Organization owner, String name) {
        Store store = new Store(null, name, "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
        store.setOrganization(owner);
        return store;
    }

    // --- schema metadata matches the JPA model ---

    @Test
    @DisplayName("employee_store_assignment table matches the JPA model: id, organization_id, employee_id, store_id")
    void assignmentTableMatchesJpaModel() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable"
                + " FROM information_schema.columns WHERE table_name = 'employee_store_assignment'"
                + " ORDER BY ordinal_position");

        assertThat(columns)
                .extracting(column -> column.get("column_name"))
                .containsExactly("id", "organization_id", "employee_id", "store_id");
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
                .containsEntry("column_name", "store_id")
                .containsEntry("data_type", "bigint")
                .containsEntry("is_nullable", "NO");
    }

    @Test
    @DisplayName("employee_store_assignment declares its named constraints: unique pair plus two composite FKs")
    void assignmentConstraintsAreNamedAndPresent() {
        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(
                "SELECT constraint_name, constraint_type"
                + " FROM information_schema.table_constraints WHERE table_name = 'employee_store_assignment'");

        assertThat(constraints)
                .extracting(row -> row.get("constraint_name") + ":" + row.get("constraint_type"))
                .contains(
                        "uk_employee_store_assignment:UNIQUE",
                        "fk_esa_employee:FOREIGN KEY",
                        "fk_esa_store:FOREIGN KEY");
        assertThat(constraints)
                .extracting(row -> row.get("constraint_type"))
                .contains("PRIMARY KEY");
    }

    @Test
    @DisplayName("fk_esa_employee is composite: (employee_id, organization_id) references employee (id, organization_id)")
    void employeeForeignKeyIsCompositeOverEmployeeAndOrganization() {
        List<String> localColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.key_column_usage"
                + " WHERE constraint_name = 'fk_esa_employee' ORDER BY ordinal_position",
                String.class);
        List<Map<String, Object>> referencedColumns = jdbcTemplate.queryForList(
                "SELECT table_name, column_name FROM information_schema.constraint_column_usage"
                + " WHERE constraint_name = 'fk_esa_employee'");

        assertThat(localColumns).containsExactly("employee_id", "organization_id");
        assertThat(referencedColumns)
                .extracting(row -> row.get("table_name") + ":" + row.get("column_name"))
                .containsExactlyInAnyOrder("employee:id", "employee:organization_id");
    }

    @Test
    @DisplayName("fk_esa_store is composite: (store_id, organization_id) references stores (id, organization_id)")
    void storeForeignKeyIsCompositeOverStoreAndOrganization() {
        List<String> localColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.key_column_usage"
                + " WHERE constraint_name = 'fk_esa_store' ORDER BY ordinal_position",
                String.class);
        List<Map<String, Object>> referencedColumns = jdbcTemplate.queryForList(
                "SELECT table_name, column_name FROM information_schema.constraint_column_usage"
                + " WHERE constraint_name = 'fk_esa_store'");

        assertThat(localColumns).containsExactly("store_id", "organization_id");
        assertThat(referencedColumns)
                .extracting(row -> row.get("table_name") + ":" + row.get("column_name"))
                .containsExactlyInAnyOrder("stores:id", "stores:organization_id");
    }

    @Test
    @DisplayName("stores carries uk_stores_id_organization so the composite FK from assignment is possible")
    void storeSupportsCompositeForeignKeyTarget() {
        List<String> uniqueColumns = jdbcTemplate.queryForList(
                "SELECT column_name FROM information_schema.key_column_usage"
                + " WHERE constraint_name = 'uk_stores_id_organization' ORDER BY ordinal_position",
                String.class);

        assertThat(uniqueColumns).containsExactly("id", "organization_id");
    }

    @Test
    @DisplayName("explicit index covers the store-side lookup column")
    void explicitIndexCoversStoreLookup() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'employee_store_assignment'", String.class);

        assertThat(indexes).contains("idx_esa_store_id");
    }

    // --- database-level enforcement ---

    @Test
    @DisplayName("uk_employee_store_assignment rejects assigning the same employee to the same store twice")
    void duplicateAssignmentIsRejectedByUniqueConstraint() {
        Organization owner = organizationRepository.save(org("INT-ESA-DUP Acme"));
        Employee employee = employeeRepository.save(employee(owner, "INT-ESA-DUP Employee"));
        Store store = storeRepository.save(store(owner, "INT-ESA-DUP Store"));
        assignmentRepository.saveAndFlush(new EmployeeStoreAssignment(employee, store));

        EmployeeStoreAssignment duplicate = new EmployeeStoreAssignment(employee, store);

        // The production detector in EmployeeStoreAssignmentService walks
        // the entire cause chain looking for "uk_employee_store_assignment";
        // asserting on the leaf cause (getMostSpecificCause()) pins the
        // strongest guarantee: PostgreSQL names the constraint on the
        // lowest-level SQLException, so any higher link in the chain still
        // surfaces the same substring.
        assertThatThrownBy(() -> assignmentRepository.saveAndFlush(duplicate))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, ex ->
                        assertThat(ex.getMostSpecificCause().getMessage())
                                .contains("uk_employee_store_assignment"));

        // Cleanup
        assignmentRepository.deleteAll(assignmentRepository.findByEmployeeId(employee.getId()));
        employeeRepository.deleteById(employee.getId());
        storeRepository.deleteById(store.getId());
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("the composite FK rejects an assignment whose organization_id does not match the employee's organization")
    void tamperedOrganizationIdIsRejectedByCompositeForeignKey() {
        Organization acme = organizationRepository.save(org("INT-ESA-XORG Acme"));
        Organization globex = organizationRepository.save(org("INT-ESA-XORG Globex"));
        Employee acmeEmployee = employeeRepository.save(employee(acme, "INT-ESA-XORG Employee"));
        Store acmeStore = storeRepository.save(store(acme, "INT-ESA-XORG Store"));
        EmployeeStoreAssignment assignment = assignmentRepository.save(
                new EmployeeStoreAssignment(acmeEmployee, acmeStore));

        // Simulate a caller trying to bypass the service check: rewrite
        // the denormalized organization_id to another organization. The
        // composite FK (employee_id, organization_id) → employee
        // (id, organization_id) refuses the update because the acme
        // employee's row still says acme.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE employee_store_assignment SET organization_id = ? WHERE id = ?",
                globex.getId(), assignment.getId()))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, ex ->
                        assertThat(ex.getMostSpecificCause().getMessage())
                                .contains("fk_esa_employee"));

        // Cleanup
        assignmentRepository.deleteById(assignment.getId());
        employeeRepository.deleteById(acmeEmployee.getId());
        storeRepository.deleteById(acmeStore.getId());
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("deleting an employee that is still assigned to a store is blocked by the FK")
    void deletingEmployeeWithAssignmentsIsBlocked() {
        Organization owner = organizationRepository.save(org("INT-ESA-EDEL Acme"));
        Employee employee = employeeRepository.save(employee(owner, "INT-ESA-EDEL Employee"));
        Store store = storeRepository.save(store(owner, "INT-ESA-EDEL Store"));
        EmployeeStoreAssignment assignment = assignmentRepository.save(
                new EmployeeStoreAssignment(employee, store));

        assertThatThrownBy(() -> employeeRepository.deleteById(employee.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: unassign first so the employee can go away.
        assignmentRepository.deleteById(assignment.getId());
        employeeRepository.deleteById(employee.getId());
        storeRepository.deleteById(store.getId());
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("deleting a store that still has assigned employees is blocked by the FK")
    void deletingStoreWithAssignmentsIsBlocked() {
        Organization owner = organizationRepository.save(org("INT-ESA-SDEL Acme"));
        Employee employee = employeeRepository.save(employee(owner, "INT-ESA-SDEL Employee"));
        Store store = storeRepository.save(store(owner, "INT-ESA-SDEL Store"));
        EmployeeStoreAssignment assignment = assignmentRepository.save(
                new EmployeeStoreAssignment(employee, store));

        assertThatThrownBy(() -> storeRepository.deleteById(store.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Cleanup: unassign first so the store can go away.
        assignmentRepository.deleteById(assignment.getId());
        employeeRepository.deleteById(employee.getId());
        storeRepository.deleteById(store.getId());
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("moving an employee that still has assignments to another organization is blocked by the composite FK")
    void movingEmployeeWithAssignmentsToAnotherOrganizationIsBlocked() {
        Organization acme = organizationRepository.save(org("INT-ESA-EMOVE Acme"));
        Organization globex = organizationRepository.save(org("INT-ESA-EMOVE Globex"));
        Employee employee = employeeRepository.save(employee(acme, "INT-ESA-EMOVE Employee"));
        Store store = storeRepository.save(store(acme, "INT-ESA-EMOVE Store"));
        EmployeeStoreAssignment assignment = assignmentRepository.save(
                new EmployeeStoreAssignment(employee, store));

        // The service layer refuses this move up front, but the composite
        // FK is the DB-level backstop: rewriting employee.organization_id
        // while an assignment row still points at (employee.id, acme.id)
        // violates fk_esa_employee because the pair no longer exists.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE employee SET organization_id = ? WHERE id = ?",
                globex.getId(), employee.getId()))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, ex ->
                        assertThat(ex.getMostSpecificCause().getMessage())
                                .contains("fk_esa_employee"));

        // Verify the employee is still attached to acme and the
        // assignment is intact (the update rolled back cleanly).
        assertThat(jdbcTemplate.queryForObject(
                "SELECT organization_id FROM employee WHERE id = ?", Long.class,
                employee.getId()))
                .isEqualTo(acme.getId());
        assertThat(assignmentRepository.countByEmployeeId(employee.getId())).isEqualTo(1L);

        // Cleanup: unassign first so the employee can be moved and removed.
        assignmentRepository.deleteById(assignment.getId());
        employeeRepository.deleteById(employee.getId());
        storeRepository.deleteById(store.getId());
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("moving a store that still has assignments to another organization is blocked by the composite FK")
    void movingStoreWithAssignmentsToAnotherOrganizationIsBlocked() {
        Organization acme = organizationRepository.save(org("INT-ESA-SMOVE Acme"));
        Organization globex = organizationRepository.save(org("INT-ESA-SMOVE Globex"));
        Employee employee = employeeRepository.save(employee(acme, "INT-ESA-SMOVE Employee"));
        Store store = storeRepository.save(store(acme, "INT-ESA-SMOVE Store"));
        EmployeeStoreAssignment assignment = assignmentRepository.save(
                new EmployeeStoreAssignment(employee, store));

        // The store-side composite FK fk_esa_store is the DB-level backstop
        // for the service guard: rewriting stores.organization_id while an
        // assignment row still points at (store.id, acme.id) violates
        // fk_esa_store because the pair no longer exists.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE stores SET organization_id = ? WHERE id = ?",
                globex.getId(), store.getId()))
                .isInstanceOfSatisfying(DataIntegrityViolationException.class, ex ->
                        assertThat(ex.getMostSpecificCause().getMessage())
                                .contains("fk_esa_store"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT organization_id FROM stores WHERE id = ?", Long.class,
                store.getId()))
                .isEqualTo(acme.getId());
        assertThat(assignmentRepository.countByStoreId(store.getId())).isEqualTo(1L);

        // Cleanup
        assignmentRepository.deleteById(assignment.getId());
        employeeRepository.deleteById(employee.getId());
        storeRepository.deleteById(store.getId());
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("removing an assignment leaves both the employee and the store intact")
    void removingAssignmentDoesNotCascadeToAggregates() {
        Organization owner = organizationRepository.save(org("INT-ESA-UNASSIGN Acme"));
        Employee employee = employeeRepository.save(employee(owner, "INT-ESA-UNASSIGN Employee"));
        Store store = storeRepository.save(store(owner, "INT-ESA-UNASSIGN Store"));
        EmployeeStoreAssignment assignment = assignmentRepository.save(
                new EmployeeStoreAssignment(employee, store));

        assignmentRepository.deleteById(assignment.getId());
        assignmentRepository.flush();

        assertThat(employeeRepository.findById(employee.getId())).isPresent();
        assertThat(storeRepository.findById(store.getId())).isPresent();

        // Cleanup
        employeeRepository.deleteById(employee.getId());
        storeRepository.deleteById(store.getId());
        organizationRepository.deleteById(owner.getId());
    }

    // --- queries used by the service layer ---

    @Test
    @DisplayName("existsByEmployeeIdAndStoreId and findByEmployeeIdAndStoreId reflect the pair")
    void pairLookupsReflectRows() {
        Organization acme = organizationRepository.save(org("INT-ESA-PAIR Acme"));
        Organization globex = organizationRepository.save(org("INT-ESA-PAIR Globex"));
        Employee acmeEmployee = employeeRepository.save(employee(acme, "INT-ESA-PAIR Acme Employee"));
        Employee globexEmployee = employeeRepository.save(employee(globex, "INT-ESA-PAIR Globex Employee"));
        Store acmeStore = storeRepository.save(store(acme, "INT-ESA-PAIR Acme Store"));
        Store globexStore = storeRepository.save(store(globex, "INT-ESA-PAIR Globex Store"));
        EmployeeStoreAssignment attached = assignmentRepository.save(
                new EmployeeStoreAssignment(acmeEmployee, acmeStore));

        assertThat(assignmentRepository.existsByEmployeeIdAndStoreId(
                acmeEmployee.getId(), acmeStore.getId())).isTrue();
        assertThat(assignmentRepository.existsByEmployeeIdAndStoreId(
                globexEmployee.getId(), globexStore.getId())).isFalse();
        assertThat(assignmentRepository.findByEmployeeIdAndStoreId(
                acmeEmployee.getId(), acmeStore.getId()))
                .get()
                .extracting(EmployeeStoreAssignment::getId)
                .isEqualTo(attached.getId());
        assertThat(assignmentRepository.findByEmployeeIdAndStoreId(
                globexEmployee.getId(), globexStore.getId())).isEmpty();

        // Cleanup
        assignmentRepository.deleteById(attached.getId());
        employeeRepository.deleteAll(List.of(acmeEmployee, globexEmployee));
        storeRepository.deleteAll(List.of(acmeStore, globexStore));
        organizationRepository.deleteById(acme.getId());
        organizationRepository.deleteById(globex.getId());
    }

    @Test
    @DisplayName("findByEmployeeId and findByStoreId return every assigned row on each side")
    void listLookupsReturnBothSides() {
        Organization owner = organizationRepository.save(org("INT-ESA-LIST Acme"));
        Employee first = employeeRepository.save(employee(owner, "INT-ESA-LIST First"));
        Employee second = employeeRepository.save(employee(owner, "INT-ESA-LIST Second"));
        Store downtown = storeRepository.save(store(owner, "INT-ESA-LIST Downtown"));
        Store airport = storeRepository.save(store(owner, "INT-ESA-LIST Airport"));
        EmployeeStoreAssignment firstDowntown = assignmentRepository.save(
                new EmployeeStoreAssignment(first, downtown));
        EmployeeStoreAssignment firstAirport = assignmentRepository.save(
                new EmployeeStoreAssignment(first, airport));
        EmployeeStoreAssignment secondDowntown = assignmentRepository.save(
                new EmployeeStoreAssignment(second, downtown));

        assertThat(assignmentRepository.findByEmployeeId(first.getId()))
                .extracting(EmployeeStoreAssignment::getId)
                .containsExactlyInAnyOrder(firstDowntown.getId(), firstAirport.getId());
        assertThat(assignmentRepository.findByStoreId(downtown.getId(), ONE_PAGE))
                .extracting(EmployeeStoreAssignment::getId)
                .containsExactlyInAnyOrder(firstDowntown.getId(), secondDowntown.getId());
        assertThat(assignmentRepository.findByStoreId(airport.getId(), ONE_PAGE))
                .extracting(EmployeeStoreAssignment::getId)
                .containsExactly(firstAirport.getId());

        // Cleanup
        assignmentRepository.deleteAll(List.of(firstDowntown, firstAirport, secondDowntown));
        employeeRepository.deleteAll(List.of(first, second));
        storeRepository.deleteAll(List.of(downtown, airport));
        organizationRepository.deleteById(owner.getId());
    }

    @Test
    @DisplayName("countByEmployeeId and countByStoreId back the delete guards")
    void countsBackTheDeleteGuards() {
        Organization owner = organizationRepository.save(org("INT-ESA-CNT Acme"));
        Employee attached = employeeRepository.save(employee(owner, "INT-ESA-CNT Attached"));
        Employee detached = employeeRepository.save(employee(owner, "INT-ESA-CNT Detached"));
        Store storeWithEmployee = storeRepository.save(store(owner, "INT-ESA-CNT Staffed"));
        Store emptyStore = storeRepository.save(store(owner, "INT-ESA-CNT Empty"));
        EmployeeStoreAssignment assignment = assignmentRepository.save(
                new EmployeeStoreAssignment(attached, storeWithEmployee));

        assertThat(assignmentRepository.countByEmployeeId(attached.getId())).isEqualTo(1L);
        assertThat(assignmentRepository.countByEmployeeId(detached.getId())).isZero();
        assertThat(assignmentRepository.countByStoreId(storeWithEmployee.getId())).isEqualTo(1L);
        assertThat(assignmentRepository.countByStoreId(emptyStore.getId())).isZero();

        // Cleanup
        assignmentRepository.deleteById(assignment.getId());
        employeeRepository.deleteAll(List.of(attached, detached));
        storeRepository.deleteAll(List.of(storeWithEmployee, emptyStore));
        organizationRepository.deleteById(owner.getId());
    }
}
