package com.example.posapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.example.posapp.entity.Store;
import com.example.posapp.exception.PosAuthFailedException;
import com.example.posapp.repository.EmployeePinCredentialRepository;
import com.example.posapp.repository.EmployeePinStoreScopeRepository;
import com.example.posapp.repository.StoreRepository;

/**
 * Unit tests for {@link PosAuthTelemetry}: the per-store and per-employee
 * counters, the fact that a lockout is reported once on entry rather than on
 * every refused request, the bounded key space, and - driven through the real
 * {@link PosAuthenticationService} - that neither the submitted PIN nor the
 * fingerprint derived from it can reach the log line.
 */
class PosAuthTelemetryTest {

    private static final Long STORE_ID = 2L;
    private static final Long EMPLOYEE_ID = 10L;
    private static final String CLIENT = "203.0.113.9";
    private static final String PIN = "0421";
    private static final String FINGERPRINT = "fp:9f2c4b-secret";

    private final PosAuthTelemetry telemetry = new PosAuthTelemetry();

    private Logger attachedLogger;
    private ListAppender<ILoggingEvent> appender;

    @AfterEach
    void detachAppender() {
        if (attachedLogger != null && appender != null) {
            attachedLogger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("failures are counted per store and, when identified, per employee")
    void countsFailuresPerStoreAndPerEmployee() {
        telemetry.failedAttempt(STORE_ID, EMPLOYEE_ID, CLIENT, false);
        telemetry.failedAttempt(STORE_ID, EMPLOYEE_ID, "203.0.113.10", false);
        telemetry.failedAttempt(STORE_ID, null, "203.0.113.11", false);

        assertThat(telemetry.failuresAt(STORE_ID)).isEqualTo(3);
        assertThat(telemetry.failuresFor(EMPLOYEE_ID)).isEqualTo(2);
    }

    @Test
    @DisplayName("a PIN that names nobody is counted against the store alone")
    void unidentifiedFailuresAreStoreOnly() {
        telemetry.failedAttempt(STORE_ID, null, CLIENT, false);
        telemetry.failedAttempt(STORE_ID, null, CLIENT, false);

        // No employee is invented for an attempt that identified none: the
        // employee counter stays empty rather than blaming somebody.
        assertThat(telemetry.failuresAt(STORE_ID)).isEqualTo(2);
        assertThat(telemetry.failuresFor(EMPLOYEE_ID)).isZero();
    }

    @Test
    @DisplayName("a lockout is logged once, when it begins, and not on the refusals after it")
    void lockoutIsLoggedOnceOnEntry() {
        captureLogs();

        telemetry.failedAttempt(STORE_ID, null, CLIENT, false);
        telemetry.failedAttempt(STORE_ID, null, CLIENT, true);
        telemetry.failedAttempt(STORE_ID, null, CLIENT, false);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.WARN);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("event=" + PosAuthTelemetry.LOCKOUT_EVENT)
                .contains("storeId=" + STORE_ID)
                .contains("client=" + CLIENT)
                .contains("storeFailures=2")
                .doesNotContain("identifiedEmployeeId");
    }

    @Test
    @DisplayName("an identified employee appears in the line with their own total")
    void identifiedEmployeeAppearsInTheLine() {
        captureLogs();

        telemetry.failedAttempt(STORE_ID, EMPLOYEE_ID, CLIENT, false);
        telemetry.failedAttempt(STORE_ID, EMPLOYEE_ID, CLIENT, true);

        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("identifiedEmployeeId=" + EMPLOYEE_ID)
                .contains("employeeFailures=2");
    }

    @Test
    @DisplayName("the lockout line carries neither the submitted PIN nor its fingerprint")
    void lockoutLineCarriesNoSecret() {
        // Driven through the real service over mocks: the property is about
        // what the authentication path hands to the logger, so asserting it
        // on the formatter alone would prove nothing.
        StoreRepository stores = mock(StoreRepository.class);
        EmployeePinStoreScopeRepository scope = mock(EmployeePinStoreScopeRepository.class);
        EmployeePinCredentialRepository credentials = mock(EmployeePinCredentialRepository.class);
        PinHasher hasher = mock(PinHasher.class);
        PosSessionService sessions = mock(PosSessionService.class);
        when(stores.findById(STORE_ID)).thenReturn(Optional.of(store()));
        when(hasher.fingerprint(PIN)).thenReturn(FINGERPRINT);
        when(scope.findByStoreIdAndPinFingerprint(STORE_ID, FINGERPRINT))
                .thenReturn(Optional.empty());
        when(hasher.hash(anyString())).thenReturn("pbkdf2:decoy");
        PosAuthRateLimiter limiter = new PosAuthRateLimiter(
                new RateLimitProperties(1, Duration.ofMinutes(5), Duration.ofMinutes(15), false, 100),
                System::currentTimeMillis);
        PosAuthenticationService service = new PosAuthenticationService(
                stores, scope, credentials, hasher, limiter, telemetry, sessions);
        captureLogs();

        assertThatThrownBy(() -> service.authenticate(STORE_ID, PIN, CLIENT))
                .isInstanceOf(PosAuthFailedException.class);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("event=" + PosAuthTelemetry.LOCKOUT_EVENT)
                .doesNotContain(PIN)
                .doesNotContain(FINGERPRINT);
    }

    @Test
    @DisplayName("the counter maps stay bounded when store IDs are arbitrary")
    void countersStayBounded() {
        for (long storeId = 1; storeId <= PosAuthTelemetry.MAX_TRACKED_KEYS + 5; storeId++) {
            telemetry.failedAttempt(storeId, null, CLIENT, false);
        }
        assertThat(telemetry.trackedStoreKeys()).isEqualTo(PosAuthTelemetry.MAX_TRACKED_KEYS);

        // A store beyond the ceiling is still worth alerting about; only its
        // tally is unavailable, and the line says so instead of claiming zero.
        captureLogs();
        telemetry.failedAttempt(9_999_999L, null, CLIENT, true);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("storeFailures=untracked");
    }

    private void captureLogs() {
        attachedLogger = (Logger) LoggerFactory.getLogger(PosAuthTelemetry.class);
        appender = new ListAppender<>();
        appender.start();
        attachedLogger.addAppender(appender);
    }

    private static Store store() {
        Store store = new Store(null, "Downtown", "US", "CA", "Los Angeles",
                "123 Main St", "90001", "America/Los_Angeles");
        store.setActive(true);
        setId(store, STORE_ID);
        return store;
    }

    private static void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
