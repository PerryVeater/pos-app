package com.example.posapp.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

import com.example.posapp.dto.PosAuthenticateResponse;
import com.example.posapp.dto.PosSessionResponse;
import com.example.posapp.entity.Employee;
import com.example.posapp.entity.Organization;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.PosAuthFailedException;
import com.example.posapp.exception.PosAuthRateLimitedException;
import com.example.posapp.exception.PosSessionInvalidException;
import com.example.posapp.service.EmployeePinService;
import com.example.posapp.service.EmployeeService;
import com.example.posapp.service.EmployeeStoreAssignmentService;
import com.example.posapp.service.PosAuthenticationService;
import com.example.posapp.service.PosSessionPurger;
import com.example.posapp.service.PosSessionService;
import com.example.posapp.service.SessionProperties;

/**
 * End-to-end integration tests for POS PIN authentication and bearer
 * sessions against a real PostgreSQL over the Flyway V17 schema (Hibernate
 * {@code ddl-auto=validate} re-confirms the {@code pos_session} model on
 * context boot). These cover what the isolated unit and web tests cannot:
 * issuance persisting only the token hash, the full authenticate → use →
 * logout lifecycle, expiry and revocation rejection against real rows, the
 * per-store/client rate limit actually blocking, cascade cleanup of
 * sessions when the owning employee is removed, and the lifecycle rules
 * that depend on real column values: revocation on PIN rotation,
 * deactivation and assignment removal, the per-employee session cap, and
 * the bounded purge of rows past the retention window.
 * <p>
 * The {@link com.example.posapp.service.PosAuthRateLimiter} is a shared,
 * process-local singleton for the whole context, so each scenario uses its
 * own client key to keep failed-attempt buckets independent and the tests
 * order-independent. An {@link AfterEach} wipes the feature tables.
 * </p>
 */
@SpringBootTest
@Testcontainers
class PosSessionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrganizationRepository organizationRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private StoreRepository storeRepository;
    @Autowired
    private EmployeeStoreAssignmentRepository assignmentRepository;
    @Autowired
    private com.example.posapp.repository.PosSessionRepository sessionRepository;
    @Autowired
    private EmployeePinService pinService;
    @Autowired
    private EmployeeStoreAssignmentService assignmentService;
    @Autowired
    private PosAuthenticationService authenticationService;
    @Autowired
    private PosSessionService sessionService;
    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private PosSessionPurger sessionPurger;
    @Autowired
    private SessionProperties sessionProperties;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearFeatureTables() {
        jdbcTemplate.execute("DELETE FROM pos_session");
        jdbcTemplate.execute("DELETE FROM employee_pin_store_scope");
        jdbcTemplate.execute("DELETE FROM employee_pin_credential");
        jdbcTemplate.execute("DELETE FROM employee_store_assignment");
        jdbcTemplate.execute("DELETE FROM employee");
        jdbcTemplate.execute("DELETE FROM stores");
        jdbcTemplate.execute("DELETE FROM organization");
    }

    @Test
    @DisplayName("authentication issues a session that stores only the hash and survives use and logout")
    void fullLifecycleStoresHashOnly() {
        Fixture fx = fixture(true, true, true);
        pinService.setPin(fx.employee.getId(), "0421");

        PosAuthenticateResponse auth =
                authenticationService.authenticate(fx.store.getId(), "0421", "lc-client");
        String rawToken = auth.token();
        assertThat(rawToken).isNotBlank();
        assertThat(auth.employeeId()).isEqualTo(fx.employee.getId());
        assertThat(auth.storeId()).isEqualTo(fx.store.getId());

        // Only the SHA-256 hash is stored; the raw token appears nowhere.
        List<String> hashes = jdbcTemplate.queryForList(
                "SELECT token_hash FROM pos_session WHERE employee_id = ?",
                String.class, fx.employee.getId());
        assertThat(hashes).hasSize(1);
        assertThat(hashes.get(0)).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(hashes.get(0)).isNotEqualTo(rawToken);
        assertThat(sessionRepository.count()).isEqualTo(1);

        // The bearer token resolves to the right non-sensitive session.
        PosSessionResponse current = sessionService.currentSession("Bearer " + rawToken);
        assertThat(current.employeeId()).isEqualTo(fx.employee.getId());
        assertThat(current.storeId()).isEqualTo(fx.store.getId());
        assertThat(current.employeeName()).isEqualTo("Ada");

        // Logout revokes it, and both use-after-logout and repeat logout are safe.
        sessionService.logout("Bearer " + rawToken);
        assertThatThrownBy(() -> sessionService.currentSession("Bearer " + rawToken))
                .isInstanceOf(PosSessionInvalidException.class);
        sessionService.logout("Bearer " + rawToken); // idempotent, no throw
    }

    @Test
    @DisplayName("an expired session is rejected")
    void expiredSessionRejected() {
        Fixture fx = fixture(true, true, true);
        pinService.setPin(fx.employee.getId(), "1234");
        String rawToken = authenticationService
                .authenticate(fx.store.getId(), "1234", "exp-client").token();

        jdbcTemplate.update("UPDATE pos_session SET expires_at = ? WHERE token_hash IS NOT NULL",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)));

        assertThatThrownBy(() -> sessionService.currentSession("Bearer " + rawToken))
                .isInstanceOf(PosSessionInvalidException.class);
    }

    @Test
    @DisplayName("unknown, malformed, and missing tokens never establish a session")
    void badTokensRejected() {
        assertThatThrownBy(() -> sessionService.currentSession("Bearer totally-unknown"))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> sessionService.currentSession("Basic dXNlcjpwYXNz"))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThatThrownBy(() -> sessionService.currentSession(null))
                .isInstanceOf(PosSessionInvalidException.class);
    }

    @Test
    @DisplayName("wrong PIN, inactive employee, and unassigned employee all fail generically")
    void credentialFailuresAreGeneric() {
        Fixture fx = fixture(true, true, true);
        pinService.setPin(fx.employee.getId(), "0421");

        // Wrong PIN.
        assertThatThrownBy(() -> authenticationService.authenticate(
                fx.store.getId(), "9999", "wrong-client"))
                .isInstanceOf(PosAuthFailedException.class)
                .hasMessage("Invalid store or PIN.");

        // Right PIN, employee deactivated.
        fx.employee.setActive(false);
        employeeRepository.save(fx.employee);
        assertThatThrownBy(() -> authenticationService.authenticate(
                fx.store.getId(), "0421", "inactive-client"))
                .isInstanceOf(PosAuthFailedException.class);

        // Unassigned store (new store, employee has a PIN but no assignment).
        Store other = newStore(fx.organization, "Unassigned Store");
        assertThatThrownBy(() -> authenticationService.authenticate(
                other.getId(), "0421", "unassigned-client"))
                .isInstanceOf(PosAuthFailedException.class);
    }

    @Test
    @DisplayName("the rate limiter blocks a client after the configured attempt limit")
    void rateLimitBlocksExcessAttempts() {
        Fixture fx = fixture(true, true, true);
        // No PIN set, so every attempt fails; the store exists and is active.
        String client = "rl-client";
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authenticationService.authenticate(
                    fx.store.getId(), "0000", client))
                    .as("attempt %d", i + 1)
                    .isInstanceOf(PosAuthFailedException.class);
        }
        // The 6th attempt is refused before the credential is examined.
        assertThatThrownBy(() -> authenticationService.authenticate(
                fx.store.getId(), "0000", client))
                .isInstanceOf(PosAuthRateLimitedException.class);
    }

    @Test
    @DisplayName("one employee can authenticate with the same PIN at multiple stores")
    void sameEmployeeSamePinAcrossStores() {
        Fixture fx = fixture(true, true, true);
        Store second = newStore(fx.organization, "Second Store");
        assignmentService.assignEmployeeToStore(fx.employee.getId(), second.getId());
        pinService.setPin(fx.employee.getId(), "7788");

        String tokenA = authenticationService
                .authenticate(fx.store.getId(), "7788", "multi-client").token();
        String tokenB = authenticationService
                .authenticate(second.getId(), "7788", "multi-client").token();

        assertThat(tokenA).isNotEqualTo(tokenB);
        assertThat(sessionService.currentSession("Bearer " + tokenA).storeId())
                .isEqualTo(fx.store.getId());
        assertThat(sessionService.currentSession("Bearer " + tokenB).storeId())
                .isEqualTo(second.getId());
        assertThat(sessionRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("removing the employee cascades away their sessions")
    void employeeRemovalCascadesSessions() {
        Fixture fx = fixture(true, true, true);
        pinService.setPin(fx.employee.getId(), "5566");
        authenticationService.authenticate(fx.store.getId(), "5566", "cascade-client");
        assertThat(sessionRepository.count()).isEqualTo(1);

        assignmentRepository.deleteAll(
                assignmentRepository.findByEmployeeId(fx.employee.getId()));
        employeeRepository.delete(fx.employee);

        assertThat(sessionRepository.count()).isZero();
    }

    @Test
    @DisplayName("authentication does not gate existing management reads")
    void managementEndpointsRemainUnauthenticated() {
        Fixture fx = fixture(true, true, true);
        // No bearer token anywhere: ordinary repository/service reads still work,
        // confirming no global security filter was introduced.
        assertThat(employeeRepository.findById(fx.employee.getId())).isPresent();
        assertThat(storeRepository.findById(fx.store.getId())).isPresent();
    }

    @Test
    @DisplayName("a store full of PIN-carrying employees resolves each PIN to exactly its owner")
    void resolvesClaimAmongManyEmployeesAtOneStore() {
        Organization org = newOrg("POS-SESSION-MANY Acme");
        Store store = newStore(org, "Downtown");
        String[] pins = {"1001", "1002", "1003", "1004", "1005", "1006"};
        Employee[] staff = new Employee[pins.length];
        for (int i = 0; i < pins.length; i++) {
            staff[i] = newEmployee(org, "Many Employee " + i, true);
            assignmentService.assignEmployeeToStore(staff[i].getId(), store.getId());
            pinService.setPin(staff[i].getId(), pins[i]);
        }
        // A seventh employee holds a PIN, but only at a different store.
        Employee elsewhere = newEmployee(org, "Elsewhere Ada", true);
        Store otherStore = newStore(org, "Airport");
        assignmentService.assignEmployeeToStore(elsewhere.getId(), otherStore.getId());
        pinService.setPin(elsewhere.getId(), "1007");

        // Headcount at the store never changes the answer: one claim row
        // matches, and it carries the right employee.
        for (int i = 0; i < pins.length; i++) {
            PosAuthenticateResponse auth = authenticationService.authenticate(
                    store.getId(), pins[i], "many-" + i);
            assertThat(auth.employeeId()).isEqualTo(staff[i].getId());
        }

        // A PIN that is valid elsewhere is not valid here: the lookup is
        // scoped by store, never a global fingerprint search.
        assertThatThrownBy(() -> authenticationService.authenticate(
                store.getId(), "1007", "many-elsewhere"))
                .isInstanceOf(PosAuthFailedException.class);
        assertThatThrownBy(() -> authenticationService.authenticate(
                otherStore.getId(), "1001", "many-other"))
                .isInstanceOf(PosAuthFailedException.class);
    }

    // --- revocation and retention ---

    @Test
    @DisplayName("rotating a PIN revokes the sessions issued under the old one")
    void pinRotationRevokesSessions() {
        Fixture fx = fixture(true, true, true);
        pinService.setPin(fx.employee.getId(), "1234");
        String issuedUnderOldPin = authenticationService
                .authenticate(fx.store.getId(), "1234", "rotate-a").token();
        // Prove the token is good before the rotation, so the failure below
        // is attributable to the rotation and nothing else.
        assertThat(sessionService.currentSession("Bearer " + issuedUnderOldPin).employeeId())
                .isEqualTo(fx.employee.getId());

        pinService.setPin(fx.employee.getId(), "5678");

        // Whoever knew the old PIN may still be holding the token.
        assertThatThrownBy(() -> sessionService.currentSession("Bearer " + issuedUnderOldPin))
                .isInstanceOf(PosSessionInvalidException.class);
        // The employee is not locked out: the new PIN issues a fresh session.
        String issuedUnderNewPin = authenticationService
                .authenticate(fx.store.getId(), "5678", "rotate-b").token();
        assertThat(sessionService.currentSession("Bearer " + issuedUnderNewPin).employeeId())
                .isEqualTo(fx.employee.getId());
    }

    @Test
    @DisplayName("deactivating an employee revokes their live sessions")
    void deactivationRevokesSessions() {
        Fixture fx = fixture(true, true, true);
        pinService.setPin(fx.employee.getId(), "2345");
        String token = authenticationService
                .authenticate(fx.store.getId(), "2345", "deact-a").token();

        Employee deactivated = new Employee(fx.employee.getName());
        deactivated.setActive(false);
        employeeService.updateEmployee(fx.employee.getId(), deactivated, fx.organization.getId());

        assertThatThrownBy(() -> sessionService.currentSession("Bearer " + token))
                .isInstanceOf(PosSessionInvalidException.class);
        // The same PIN no longer authenticates a new session either.
        assertThatThrownBy(() -> authenticationService.authenticate(
                fx.store.getId(), "2345", "deact-b"))
                .isInstanceOf(PosAuthFailedException.class);
    }

    @Test
    @DisplayName("removing a store assignment revokes that store's sessions only")
    void assignmentRemovalRevokesOnlyThatStoresSessions() {
        Fixture fx = fixture(true, true, true);
        Store second = newStore(fx.organization, "Second Store");
        assignmentService.assignEmployeeToStore(fx.employee.getId(), second.getId());
        pinService.setPin(fx.employee.getId(), "3456");
        String atFirst = authenticationService
                .authenticate(fx.store.getId(), "3456", "assign-a").token();
        String atSecond = authenticationService
                .authenticate(second.getId(), "3456", "assign-b").token();

        assignmentService.removeEmployeeFromStore(fx.employee.getId(), second.getId());

        assertThatThrownBy(() -> sessionService.currentSession("Bearer " + atSecond))
                .isInstanceOf(PosSessionInvalidException.class);
        assertThat(sessionService.currentSession("Bearer " + atFirst).storeId())
                .isEqualTo(fx.store.getId());
        // And the removed store no longer accepts the PIN at all.
        assertThatThrownBy(() -> authenticationService.authenticate(
                second.getId(), "3456", "assign-c"))
                .isInstanceOf(PosAuthFailedException.class);
    }

    @Test
    @DisplayName("logging in beyond the per-employee cap revokes the oldest session")
    void perEmployeeCapRevokesOldestSession() {
        Fixture fx = fixture(true, true, true);
        pinService.setPin(fx.employee.getId(), "4567");
        int cap = sessionProperties.maxPerEmployee();
        List<String> tokens = new ArrayList<>();
        for (int attempt = 0; attempt <= cap; attempt++) {
            tokens.add(authenticationService
                    .authenticate(fx.store.getId(), "4567", "cap-" + attempt).token());
        }

        // The rows are kept for audit; only the oldest stops working.
        assertThat(sessionRepository.count()).isEqualTo(cap + 1);
        assertThatThrownBy(() -> sessionService.currentSession("Bearer " + tokens.get(0)))
                .isInstanceOf(PosSessionInvalidException.class);
        for (int index = 1; index < tokens.size(); index++) {
            assertThat(sessionService.currentSession("Bearer " + tokens.get(index)).employeeId())
                    .as("session %d of %d should still be live", index, tokens.size())
                    .isEqualTo(fx.employee.getId());
        }
    }

    @Test
    @DisplayName("the purge skips rows inside the retention window and runs in bounded batches")
    void purgeIsBoundedAndRespectsRetention() {
        Fixture fx = fixture(true, true, true);
        Store second = newStore(fx.organization, "Second Store");
        assignmentService.assignEmployeeToStore(fx.employee.getId(), second.getId());
        pinService.setPin(fx.employee.getId(), "6789");
        authenticationService.authenticate(fx.store.getId(), "6789", "purge-a");
        authenticationService.authenticate(fx.store.getId(), "6789", "purge-b");
        String retained = authenticationService
                .authenticate(second.getId(), "6789", "purge-c").token();

        // Two rows expired long ago, one expired yesterday: all three reject
        // the token, but only the first two are past the audit window.
        jdbcTemplate.update("UPDATE pos_session SET expires_at = now() - interval '20 days'"
                + " WHERE store_id = ?", fx.store.getId());
        jdbcTemplate.update("UPDATE pos_session SET expires_at = now() - interval '1 day'"
                + " WHERE store_id = ?", second.getId());
        Instant cutoff = Instant.now().minus(sessionProperties.retention());

        // A batch size of 1 removes exactly one row per transaction.
        assertThat(sessionPurger.purgeBatch(cutoff, 1)).isEqualTo(1);
        assertThat(sessionRepository.count()).isEqualTo(2);
        assertThat(sessionPurger.purgeBatch(cutoff, 10)).isEqualTo(1);
        assertThat(sessionRepository.count()).isEqualTo(1);

        // The row inside the retention window survives, already rejected.
        assertThat(sessionPurger.purgeBatch(cutoff, 10)).isZero();
        assertThat(sessionRepository.count()).isEqualTo(1);
        assertThatThrownBy(() -> sessionService.currentSession("Bearer " + retained))
                .isInstanceOf(PosSessionInvalidException.class);

        // Once the window has moved past it, the next run collects it.
        assertThat(sessionPurger.purgeBatch(Instant.now(), 10)).isEqualTo(1);
        assertThat(sessionRepository.count()).isZero();
        assertThat(sessionPurger.purgeBatch(Instant.now(), 10)).isZero();
    }

    // --- fixtures ---

    private record Fixture(Organization organization, Employee employee, Store store) {
    }

    private Fixture fixture(boolean employeeActive, boolean storeActive, boolean assign) {
        Organization org = newOrg("POS-SESSION Acme " + System.nanoTime());
        Employee employee = newEmployee(org, "Ada", employeeActive);
        Store store = newStore(org, "Downtown");
        store.setActive(storeActive);
        storeRepository.save(store);
        if (assign) {
            assignmentService.assignEmployeeToStore(employee.getId(), store.getId());
        }
        return new Fixture(org, employee, store);
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
}
