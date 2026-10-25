package com.example.posapp.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

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
import com.example.posapp.entity.PinSecretAnchor;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.PosAuthFailedException;
import com.example.posapp.service.EmployeePinService;
import com.example.posapp.service.EmployeeStoreAssignmentService;
import com.example.posapp.service.PinHasher;
import com.example.posapp.service.PinProperties;
import com.example.posapp.service.PinSecretAnchorWriter;
import com.example.posapp.service.PinSecretGuard;
import com.example.posapp.service.PosAuthenticationService;

/**
 * End-to-end tests for the PIN fingerprint anchor (Flyway V21) against a real
 * PostgreSQL: the context boot anchors the configured secret, a changed
 * secret is refused instead of silently locking everyone out, and an armed
 * reset drops the claims so that no PIN can authenticate until it is set
 * again.
 * <p>
 * The class boots the real application, so the guard's {@code @PostConstruct}
 * has already run by the time these tests start - which is the first
 * assertion. Rotation is then exercised by constructing a guard over the
 * same writer with a different secret, because a bound
 * {@link PinProperties} cannot be changed under a running context.
 * </p>
 */
@SpringBootTest
@Testcontainers
class PinSecretAnchorIntegrationTest {

    private static final String ROTATED_SECRET = "rotated-pin-fingerprint-secret";
    private static final String PIN = "0421";
    private static final String CLIENT = "203.0.113.9";

    /**
     * Disposable PostgreSQL 16 database shared by the class; wired into the
     * Spring datasource through {@code @ServiceConnection}.
     */
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PinSecretAnchorWriter anchorWriter;

    @Autowired
    private PinHasher pinHasher;

    @Autowired
    private PinProperties pinProperties;

    @Autowired
    private EmployeePinService pinService;

    @Autowired
    private EmployeeStoreAssignmentService assignmentService;

    @Autowired
    private PosAuthenticationService authenticationService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreAnchorAndClearFeatureTables() {
        // The reset test re-anchors under a different secret; put the
        // configured one back so the shared context's anchor matches again.
        anchorWriter.reset(pinHasher.hash(pinProperties.hmacSecret()), Instant.now());
        jdbcTemplate.execute("DELETE FROM employee_pin_store_scope");
        jdbcTemplate.execute("DELETE FROM employee_pin_credential");
        jdbcTemplate.execute("DELETE FROM employee_store_assignment");
        jdbcTemplate.execute("DELETE FROM employee");
        jdbcTemplate.execute("DELETE FROM stores");
        jdbcTemplate.execute("DELETE FROM organization");
    }

    @Test
    @DisplayName("booting the application anchors the configured secret exactly once")
    void contextBootAnchorsTheConfiguredSecret() {
        assertThat(anchorCount()).isEqualTo(1);

        String anchored = jdbcTemplate.queryForObject(
                "SELECT secret_hash FROM pos_pin_secret_anchor WHERE id = 1", String.class);

        // The anchor is a salted hash that verifies the configured secret,
        // and it is not the secret itself.
        assertThat(anchored).startsWith("pbkdf2:").doesNotContain(pinProperties.hmacSecret());
        assertThat(pinHasher.verify(pinProperties.hmacSecret(), anchored)).isTrue();
    }

    @Test
    @DisplayName("a changed secret refuses to start and leaves the claims untouched")
    void changedSecretIsRefusedWithoutTouchingClaims() {
        Organization org = newOrg("INT-ANCHOR-REFUSE Acme");
        Store store = newStore(org, "INT-ANCHOR-REFUSE Store");
        Employee employee = newEmployee(org, "INT-ANCHOR-REFUSE Ada");
        assignmentService.assignEmployeeToStore(employee.getId(), store.getId());
        pinService.setPin(employee.getId(), PIN);

        assertThat(claimCount()).isEqualTo(1);

        PinSecretGuard refusingGuard = guard(ROTATED_SECRET, false);
        assertThatThrownBy(refusingGuard::verifyOrAnchor)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pos.pin.fingerprint-reset=true");

        // Refusing is non-destructive: the operator can put the old secret
        // back and the data picks up exactly where it was.
        assertThat(claimCount()).isEqualTo(1);
        assertThat(pinStillAuthenticates(store)).isTrue();
    }

    @Test
    @DisplayName("an armed reset drops every claim, re-anchors, and locks out the old PIN")
    void armedResetDropsClaimsAndReanchors() {
        Organization org = newOrg("INT-ANCHOR-RESET Acme");
        Store store = newStore(org, "INT-ANCHOR-RESET Store");
        Employee employee = newEmployee(org, "INT-ANCHOR-RESET Ada");
        assignmentService.assignEmployeeToStore(employee.getId(), store.getId());
        pinService.setPin(employee.getId(), PIN);
        assertThat(claimCount()).isEqualTo(1);

        guard(ROTATED_SECRET, true).verifyOrAnchor();

        // The claims cannot be recomputed - the PIN is not recoverable - so
        // they are gone, the anchor holds the new secret, and the PIN that
        // used to work no longer does.
        assertThat(claimCount()).isZero();
        assertThat(anchorCount()).isEqualTo(1);
        String reanchored = jdbcTemplate.queryForObject(
                "SELECT secret_hash FROM pos_pin_secret_anchor WHERE id = 1", String.class);
        assertThat(pinHasher.verify(ROTATED_SECRET, reanchored)).isTrue();
        assertThatThrownBy(() -> authenticationService.authenticate(store.getId(), PIN, CLIENT))
                .isInstanceOf(PosAuthFailedException.class);
    }

    @Test
    @DisplayName("the credential survives a reset, so re-setting the PIN only rewrites the claim")
    void resetKeepsTheCredentialForReenrollment() {
        Organization org = newOrg("INT-ANCHOR-REENROLL Acme");
        Store store = newStore(org, "INT-ANCHOR-REENROLL Store");
        Employee employee = newEmployee(org, "INT-ANCHOR-REENROLL Ada");
        assignmentService.assignEmployeeToStore(employee.getId(), store.getId());
        pinService.setPin(employee.getId(), PIN);

        guard(ROTATED_SECRET, true).verifyOrAnchor();
        assertThat(claimCount()).isZero();
        // Only the claims were dropped: the salted credential is independent
        // of the fingerprint key, so re-enrollment is an update, not a new
        // enrollment.
        assertThat(credentialCount()).isEqualTo(1);

        // Re-enrollment is the supported recovery, and it does not need the
        // credential to be recreated: setting the PIN writes the claim back
        // under whichever secret the running application holds.
        pinService.setPin(employee.getId(), PIN);

        assertThat(claimCount()).isEqualTo(1);
        assertThat(credentialCount()).isEqualTo(1);
    }

    private PinSecretGuard guard(String secret, boolean armed) {
        PinProperties properties = new PinProperties(4, 6, secret, 1000, armed);
        return new PinSecretGuard(properties, new PinHasher(properties), anchorWriter);
    }

    private boolean pinStillAuthenticates(Store store) {
        // The context's service still holds the configured secret, so a live
        // claim is what makes this succeed: a 401 means the claim is gone.
        return authenticationService.authenticate(store.getId(), PIN, CLIENT) != null;
    }

    private long anchorCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pos_pin_secret_anchor", Long.class);
        return count == null ? 0 : count;
    }

    private long claimCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM employee_pin_store_scope", Long.class);
        return count == null ? 0 : count;
    }

    private long credentialCount() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM employee_pin_credential", Long.class);
        return count == null ? 0 : count;
    }

    private Organization newOrg(String name) {
        return organizationRepository.save(new Organization(name));
    }

    private Employee newEmployee(Organization owner, String name) {
        Employee employee = new Employee(name);
        employee.setOrganization(owner);
        employee.setActive(true);
        return employeeRepository.save(employee);
    }

    private Store newStore(Organization owner, String name) {
        Store store = new Store(null, name, "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
        store.setOrganization(owner);
        return storeRepository.save(store);
    }
}
