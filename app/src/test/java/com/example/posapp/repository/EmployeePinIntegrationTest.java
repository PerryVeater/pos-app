package com.example.posapp.repository;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.exception.EmployeePinValidationException;
import com.example.posapp.exception.EmployeeStoreValidationException;
import com.example.posapp.exception.StoreNotFoundException;
import com.example.posapp.service.EmployeePinService;
import com.example.posapp.service.EmployeeStoreAssignmentService;

/**
 * End-to-end integration tests for employee POS PIN credentials and their
 * store-scoped uniqueness against a real PostgreSQL.
 * <p>
 * These drive the real {@link EmployeePinService} and
 * {@link EmployeeStoreAssignmentService} over the Flyway V16 schema
 * (Hibernate {@code ddl-auto=validate} already accepted the model, which
 * this context boot re-confirms), so they cover what the isolated unit and
 * web tests cannot: the {@code uk_epss_store_fingerprint} guarantee across
 * employees and stores, the atomic assignment-plus-claim path, the
 * PIN-frees-on-removal behavior, the absence of plaintext anywhere in
 * storage, and a genuine race between two concurrent setters. The methods
 * are intentionally not transactional; an {@link AfterEach} wipes the
 * feature tables so each test starts from a clean slate.
 * </p>
 * <p>
 * Both services are brand-scoped, so every call also passes the owning
 * organization's ID - the caller's brand. These tests exercise the real
 * rows, so a brand mismatch would surface as a genuine not-found rather
 * than a shortcut.
 * </p>
 */
@SpringBootTest
@Testcontainers
class EmployeePinIntegrationTest {

    /**
     * Disposable PostgreSQL 16 database shared by the class; wired into the
     * Spring datasource through {@code @ServiceConnection}.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EmployeeStoreAssignmentRepository assignmentRepository;

    @Autowired
    private EmployeePinService pinService;

    @Autowired
    private EmployeeStoreAssignmentService assignmentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearFeatureTables() {
        // Children first, then the aggregates. FK cascades would also cover
        // the PIN tables, but explicit deletes keep ordering obvious and
        // independent of which path created the rows.
        jdbcTemplate.execute("DELETE FROM employee_pin_store_scope");
        jdbcTemplate.execute("DELETE FROM employee_pin_credential");
        jdbcTemplate.execute("DELETE FROM employee_store_assignment");
        jdbcTemplate.execute("DELETE FROM employee");
        jdbcTemplate.execute("DELETE FROM stores");
        jdbcTemplate.execute("DELETE FROM organization");
    }

    private Organization newOrg(String name) {
        return organizationRepository.save(new Organization(name));
    }

    private Employee newEmployee(Organization owner, String name, boolean active) {
        Employee employee = new Employee(name);
        employee.setOrganization(owner);
        employee.setActive(active);
        return employeeRepository.save(employee);
    }

    private Store newStore(Organization owner, String name) {
        Store store = new Store(null, name, "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
        store.setOrganization(owner);
        return storeRepository.save(store);
    }

    private long credentialCount(Long employeeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM employee_pin_credential WHERE employee_id = ?",
                Integer.class, employeeId);
        return count == null ? 0 : count;
    }

    private long claimCountAtStore(Long storeId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM employee_pin_store_scope WHERE store_id = ?",
                Integer.class, storeId);
        return count == null ? 0 : count;
    }

    // --- setting, changing, and format rules ---

    @Test
    @DisplayName("setting then changing a valid PIN stores only hashed material, never plaintext")
    void setAndChangePinStoresNoPlaintext() {
        Organization org = newOrg("INT-PIN-SET Acme");
        Employee employee = newEmployee(org, "INT-PIN-SET Ada", true);

        pinService.setPin(employee.getId(), "0421", org.getId());
        pinService.setPin(employee.getId(), "135790", org.getId());

        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT pin_hash FROM employee_pin_credential WHERE employee_id = ?",
                String.class, employee.getId());
        List<String> fingerprints = jdbcTemplate.queryForList(
                "SELECT pin_fingerprint FROM employee_pin_credential WHERE employee_id = ?",
                String.class, employee.getId());

        assertThat(hashes).hasSize(1);
        assertThat(fingerprints).hasSize(1);
        // The last-written credential survives the change; it is a salted
        // PBKDF2 hash, and neither column leaks either plaintext PIN.
        assertThat(hashes.get(0)).startsWith("pbkdf2:");
        assertThat(hashes.get(0)).doesNotContain("0421").doesNotContain("135790");
        assertThat(fingerprints.get(0)).doesNotContain("0421").doesNotContain("135790");
        assertThat(fingerprints.get(0)).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("a change to a different PIN retargets existing store claims in place")
    void changePinRetargetsClaims() {
        Organization org = newOrg("INT-PIN-CHG Acme");
        Employee employee = newEmployee(org, "INT-PIN-CHG Ada", true);
        Store store = newStore(org, "INT-PIN-CHG Store");
        assignmentService.assignEmployeeToStore(employee.getId(), store.getId(), org.getId());

        pinService.setPin(employee.getId(), "1111", org.getId());
        pinService.setPin(employee.getId(), "2222", org.getId());

        // Still exactly one claim row for the single employee-store, and no
        // duplicate despite two setPin calls (the (employee_id, store_id)
        // unique would fire if the change inserted instead of updating).
        assertThat(claimCountAtStore(store.getId())).isEqualTo(1);
        Long claimsForEmployee = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM employee_pin_store_scope WHERE employee_id = ?",
                Long.class, employee.getId());
        assertThat(claimsForEmployee).isEqualTo(1L);
    }

    @Test
    @DisplayName("malformed PINs are rejected and leave no credential")
    void malformedPinsRejected() {
        Organization org = newOrg("INT-PIN-BAD Acme");
        Employee employee = newEmployee(org, "INT-PIN-BAD Ada", true);

        for (String bad : new String[]{"12", "1234567", "12a4", "1234 "}) {
            assertThatThrownBy(() -> pinService.setPin(employee.getId(), bad, org.getId()))
                    .as("PIN '%s'", bad)
                    .isInstanceOf(EmployeePinValidationException.class);
        }
        assertThat(credentialCount(employee.getId())).isZero();
    }

    @Test
    @DisplayName("a missing employee cannot get a PIN")
    void missingEmployeeRejected() {
        assertThatThrownBy(() -> pinService.setPin(999999L, "1234", 999999L))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    @Test
    @DisplayName("an inactive employee cannot get a PIN")
    void inactiveEmployeeRejected() {
        Organization org = newOrg("INT-PIN-INACT Acme");
        Employee employee = newEmployee(org, "INT-PIN-INACT Bob", false);

        assertThatThrownBy(() -> pinService.setPin(employee.getId(), "1234", org.getId()))
                .isInstanceOf(EmployeePinValidationException.class)
                .hasMessageContaining("active");
        assertThat(credentialCount(employee.getId())).isZero();
    }

    // --- store-scoped uniqueness ---

    @Test
    @DisplayName("the same PIN is accepted for two employees assigned to different stores")
    void samePinDifferentStoresAllowed() {
        Organization org = newOrg("INT-PIN-DIFF Acme");
        Employee ada = newEmployee(org, "INT-PIN-DIFF Ada", true);
        Employee bob = newEmployee(org, "INT-PIN-DIFF Bob", true);
        Store downtown = newStore(org, "INT-PIN-DIFF Downtown");
        Store airport = newStore(org, "INT-PIN-DIFF Airport");
        assignmentService.assignEmployeeToStore(ada.getId(), downtown.getId(), org.getId());
        assignmentService.assignEmployeeToStore(bob.getId(), airport.getId(), org.getId());

        pinService.setPin(ada.getId(), "1234", org.getId());
        pinService.setPin(bob.getId(), "1234", org.getId());

        assertThat(claimCountAtStore(downtown.getId())).isEqualTo(1);
        assertThat(claimCountAtStore(airport.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("the same PIN is rejected for two employees sharing a store")
    void samePinSharedStoreRejected() {
        Organization org = newOrg("INT-PIN-SHARE Acme");
        Employee ada = newEmployee(org, "INT-PIN-SHARE Ada", true);
        Employee bob = newEmployee(org, "INT-PIN-SHARE Bob", true);
        Store store = newStore(org, "INT-PIN-SHARE Store");
        assignmentService.assignEmployeeToStore(ada.getId(), store.getId(), org.getId());
        assignmentService.assignEmployeeToStore(bob.getId(), store.getId(), org.getId());

        pinService.setPin(ada.getId(), "1234", org.getId());

        assertThatThrownBy(() -> pinService.setPin(bob.getId(), "1234", org.getId()))
                .isInstanceOf(EmployeePinValidationException.class)
                .hasMessageContaining("already in use");
        // Bob keeps no credential and the loser's claim was rolled back.
        assertThat(credentialCount(bob.getId())).isZero();
        assertThat(claimCountAtStore(store.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("one employee reuses the same PIN across every assigned store")
    void sameEmployeeSamePinAcrossStores() {
        Organization org = newOrg("INT-PIN-MULTI Acme");
        Employee ada = newEmployee(org, "INT-PIN-MULTI Ada", true);
        Store downtown = newStore(org, "INT-PIN-MULTI Downtown");
        Store airport = newStore(org, "INT-PIN-MULTI Airport");
        Store harbor = newStore(org, "INT-PIN-MULTI Harbor");
        assignmentService.assignEmployeeToStore(ada.getId(), downtown.getId(), org.getId());
        assignmentService.assignEmployeeToStore(ada.getId(), airport.getId(), org.getId());
        assignmentService.assignEmployeeToStore(ada.getId(), harbor.getId(), org.getId());

        pinService.setPin(ada.getId(), "1234", org.getId());

        assertThat(claimCountAtStore(downtown.getId())).isEqualTo(1);
        assertThat(claimCountAtStore(airport.getId())).isEqualTo(1);
        assertThat(claimCountAtStore(harbor.getId())).isEqualTo(1);
        // All three claims carry the same fingerprint for one employee.
        Long distinctFingerprints = jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT pin_fingerprint) FROM employee_pin_store_scope"
                        + " WHERE employee_id = ?", Long.class, ada.getId());
        assertThat(distinctFingerprints).isEqualTo(1L);
    }

    @Test
    @DisplayName("an assignment that would create a PIN conflict is rejected with no partial assignment")
    void assignmentCausingConflictRejected() {
        Organization org = newOrg("INT-PIN-ASSIGN Acme");
        Employee ada = newEmployee(org, "INT-PIN-ASSIGN Ada", true);
        Employee bob = newEmployee(org, "INT-PIN-ASSIGN Bob", true);
        Store downtown = newStore(org, "INT-PIN-ASSIGN Downtown");
        Store airport = newStore(org, "INT-PIN-ASSIGN Airport");

        assignmentService.assignEmployeeToStore(ada.getId(), downtown.getId(), org.getId());
        pinService.setPin(ada.getId(), "1234", org.getId());
        // Bob sets the same PIN while only at a different store: allowed.
        assignmentService.assignEmployeeToStore(bob.getId(), airport.getId(), org.getId());
        pinService.setPin(bob.getId(), "1234", org.getId());

        // Moving Bob into Downtown, where Ada already claims PIN 1234,
        // must fail and leave Bob unassigned to Downtown.
        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(
                bob.getId(), downtown.getId(), org.getId()))
                .isInstanceOf(EmployeeStoreValidationException.class)
                .hasMessageContaining("conflict");
        assertThat(assignmentRepository.existsByEmployeeIdAndStoreId(
                bob.getId(), downtown.getId())).isFalse();
        assertThat(claimCountAtStore(downtown.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("removing a PIN frees it for another employee at the same store")
    void removalFreesPinForOthers() {
        Organization org = newOrg("INT-PIN-FREE Acme");
        Employee ada = newEmployee(org, "INT-PIN-FREE Ada", true);
        Employee bob = newEmployee(org, "INT-PIN-FREE Bob", true);
        Store store = newStore(org, "INT-PIN-FREE Store");
        assignmentService.assignEmployeeToStore(ada.getId(), store.getId(), org.getId());
        assignmentService.assignEmployeeToStore(bob.getId(), store.getId(), org.getId());

        pinService.setPin(ada.getId(), "1234", org.getId());
        pinService.removePin(ada.getId(), org.getId());
        assertThat(claimCountAtStore(store.getId())).isZero();

        // The same PIN is now free at the shared store.
        pinService.setPin(bob.getId(), "1234", org.getId());
        assertThat(claimCountAtStore(store.getId())).isEqualTo(1);
        assertThat(credentialCount(bob.getId())).isEqualTo(1);
    }

    // --- compatibility with no-PIN employees ---

    @Test
    @DisplayName("employees without a PIN assign and unassign normally")
    void noPinAssignmentUnaffected() {
        Organization org = newOrg("INT-PIN-NOPIN Acme");
        Employee ada = newEmployee(org, "INT-PIN-NOPIN Ada", true);
        Employee bob = newEmployee(org, "INT-PIN-NOPIN Bob", true);
        Store store = newStore(org, "INT-PIN-NOPIN Store");

        assignmentService.assignEmployeeToStore(ada.getId(), store.getId(), org.getId());
        assignmentService.assignEmployeeToStore(bob.getId(), store.getId(), org.getId());
        // Idempotent re-assign still succeeds.
        assignmentService.assignEmployeeToStore(ada.getId(), store.getId(), org.getId());
        assignmentService.removeEmployeeFromStore(ada.getId(), store.getId(), org.getId());

        assertThat(claimCountAtStore(store.getId())).isZero();
        assertThat(assignmentRepository.existsByEmployeeIdAndStoreId(bob.getId(), store.getId())).isTrue();
    }

    @Test
    @DisplayName("assigning a missing store is rejected")
    void assignMissingStoreRejected() {
        Organization org = newOrg("INT-PIN-MISSSTORE Acme");
        Employee ada = newEmployee(org, "INT-PIN-MISSSTORE Ada", true);

        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(
                ada.getId(), 999999L, org.getId()))
                .isInstanceOf(StoreNotFoundException.class);
    }

    @Test
    @DisplayName("assigning a store in another brand is rejected as missing")
    void assignStoreInAnotherBrandRejected() {
        Organization org = newOrg("INT-PIN-BRAND Acme");
        Organization other = newOrg("INT-PIN-BRAND Globex");
        Employee ada = newEmployee(org, "INT-PIN-BRAND Ada", true);
        Store foreignStore = newStore(other, "INT-PIN-BRAND Store");

        // The store exists, but not in Ada's brand: the scoped lookup is
        // empty, so this must be indistinguishable from a missing store.
        assertThatThrownBy(() -> assignmentService.assignEmployeeToStore(
                ada.getId(), foreignStore.getId(), org.getId()))
                .isInstanceOf(StoreNotFoundException.class);
        assertThat(assignmentRepository.existsByEmployeeIdAndStoreId(
                ada.getId(), foreignStore.getId())).isFalse();
    }

    // --- concurrency ---

    @Test
    @DisplayName("concurrent setters of the same PIN at one store: exactly one wins, no partial state")
    void concurrentSamePinSettersSingleWinner() throws Exception {
        Organization org = newOrg("INT-PIN-RACE Acme");
        Employee ada = newEmployee(org, "INT-PIN-RACE Ada", true);
        Employee bob = newEmployee(org, "INT-PIN-RACE Bob", true);
        Store store = newStore(org, "INT-PIN-RACE Store");
        assignmentService.assignEmployeeToStore(ada.getId(), store.getId(), org.getId());
        assignmentService.assignEmployeeToStore(bob.getId(), store.getId(), org.getId());

        String racingPin = "9999";
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<String> adaResult = pool.submit(
                    namedRacer(ada.getId(), racingPin, org.getId(), barrier));
            Future<String> bobResult = pool.submit(
                    namedRacer(bob.getId(), racingPin, org.getId(), barrier));
            String first = adaResult.get(10, TimeUnit.SECONDS);
            String second = bobResult.get(10, TimeUnit.SECONDS);

            // Exactly one employee ends up owning the PIN at the store; the
            // other is rejected and, because credential + claim share one
            // transaction, the loser holds no credential row at all.
            long okCount = (first.startsWith("ok") ? 1 : 0) + (second.startsWith("ok") ? 1 : 0);
            long conflictCount = (first.startsWith("conflict") ? 1 : 0)
                    + (second.startsWith("conflict") ? 1 : 0);
            assertThat(okCount).isEqualTo(1);
            assertThat(conflictCount).isEqualTo(1);
            assertThat(claimCountAtStore(store.getId())).isEqualTo(1);
            assertThat(credentialCount(ada.getId()) + credentialCount(bob.getId())).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Build a callable that gates on the shared barrier then attempts to set
     * the racing PIN for one employee, reporting success or the friendly
     * conflict so the test can assert exactly one winner.
     * @param employeeId the employee this racer sets the PIN for
     * @param racingPin the PIN both racers try to claim
     * @param brandId the organization the simulated caller acts within
     * @param barrier the start gate forcing the two writes to overlap
     * @return a callable returning "ok:id" or "conflict:id"
     */
    private Callable<String> namedRacer(Long employeeId, String racingPin,
                                        Long brandId, CyclicBarrier barrier) {
        return () -> {
            barrier.await(5, TimeUnit.SECONDS);
            try {
                pinService.setPin(employeeId, racingPin, brandId);
                return "ok:" + employeeId;
            } catch (EmployeePinValidationException ex) {
                return "conflict:" + employeeId;
            }
        };
    }
}
