package com.example.posapp.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Counts failed POS PIN attempts and emits the one line that tells an
 * operator whether a lockout was a shift change or an attack.
 * <p>
 * Two counters, both cumulative for the life of the process: failed attempts
 * per store, and failed attempts attributed to an employee. The second one
 * only exists when the submitted PIN's fingerprint matched a claim, because
 * that is the only case in which the server knows whose PIN was guessed. A
 * PIN that matches nothing cannot be attributed to anybody, and inventing an
 * attribution would be a lie an operator might act on - "unknown person
 * guessing" and "someone targeting Ada" are different incidents.
 * </p>
 * <p>
 * <b>What is logged:</b> one {@code WARN} line, once, when a failure begins a
 * lockout ({@code event=pos_auth_lockout}) - not on every refused request and
 * not on ordinary mistakes. The line is {@code key=value} pairs so it can be
 * filtered and counted without a parser: the store, the client, the store's
 * failure total and, when one was identified, the employee and their total.
 * The PIN and the fingerprint derived from it are never part of the API here
 * and therefore can never reach the log; there is deliberately no parameter
 * that could carry them.
 * </p>
 * <p>
 * <b>Bounded memory:</b> store IDs arrive from the request path and may name
 * stores that do not exist, so the per-store map is capped at
 * {@link #MAX_TRACKED_KEYS} keys. Once full, a new key is not tracked and is
 * reported as {@code untracked} rather than as a count of zero - a lockout is
 * still logged, because the alert matters more than the tally. This is the
 * in-process slice of D18: M7 replaces it with real metrics.
 * </p>
 */
@Component
public class PosAuthTelemetry {

    private static final Logger LOG = LoggerFactory.getLogger(PosAuthTelemetry.class);

    /** Event name carried by the structured line, {@code event=...}. */
    static final String LOCKOUT_EVENT = "pos_auth_lockout";

    /** Rendered when a counter could not be recorded because the map was full. */
    static final int UNTRACKED = -1;

    /** Ceiling on tracked store and employee counters. */
    static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<Long, AtomicInteger> storeFailures = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> employeeFailures = new ConcurrentHashMap<>();

    /**
     * Record a failed authentication attempt and, when this failure is the
     * one that began a lockout, log the lockout line.
     * @param storeId the store that was authenticated against
     * @param identifiedEmployeeId the employee the submitted PIN belonged
     *        to, or {@code null} when no claim matched
     * @param clientKey the resolved client identifier
     * @param lockoutEntered whether this failure began a lockout
     */
    public void failedAttempt(Long storeId, Long identifiedEmployeeId, String clientKey,
                              boolean lockoutEntered) {
        int storeCount = increment(storeFailures, storeId);
        int employeeCount = identifiedEmployeeId == null
                ? 0
                : increment(employeeFailures, identifiedEmployeeId);
        if (lockoutEntered) {
            LOG.warn(lockoutLine(storeId, clientKey, storeCount, identifiedEmployeeId, employeeCount));
        }
    }

    /**
     * Render the lockout line. One pure formatter, so the shape asserted by
     * the tests is the shape that gets logged.
     * @param storeId the store that was authenticated against
     * @param clientKey the resolved client identifier, possibly {@code null}
     * @param trackedStoreFailures the store's cumulative failure count, or
     *        {@link #UNTRACKED}
     * @param identifiedEmployeeId the identified employee, or {@code null}
     * @param trackedEmployeeFailures that employee's cumulative failure count,
     *        or {@link #UNTRACKED}
     * @return the line, without a trailing newline
     */
    static String lockoutLine(Long storeId, String clientKey, int trackedStoreFailures,
                              Long identifiedEmployeeId, int trackedEmployeeFailures) {
        StringBuilder line = new StringBuilder()
                .append("event=").append(LOCKOUT_EVENT)
                .append(" storeId=").append(storeId)
                .append(" client=").append(clientKey == null ? "unknown" : clientKey)
                .append(" storeFailures=").append(render(trackedStoreFailures));
        if (identifiedEmployeeId != null) {
            line.append(" identifiedEmployeeId=").append(identifiedEmployeeId)
                    .append(" employeeFailures=").append(render(trackedEmployeeFailures));
        }
        return line.toString();
    }

    /**
     * Failed attempts counted for a store. Exposed for tests and diagnostics.
     * @param storeId the store ID
     * @return the cumulative count, {@code 0} when the store was never counted
     */
    int failuresAt(Long storeId) {
        AtomicInteger count = storeFailures.get(storeId);
        return count == null ? 0 : count.get();
    }

    /**
     * Failed attempts attributed to an employee. Exposed for tests and
     * diagnostics.
     * @param employeeId the employee ID
     * @return the cumulative count, {@code 0} when the employee was never
     *         identified in a failed attempt
     */
    int failuresFor(Long employeeId) {
        AtomicInteger count = employeeFailures.get(employeeId);
        return count == null ? 0 : count.get();
    }

    /**
     * Number of store counters held. Exposed for tests and diagnostics.
     * @return the tracked-key count
     */
    int trackedStoreKeys() {
        return storeFailures.size();
    }

    private static String render(int count) {
        return count == UNTRACKED ? "untracked" : Integer.toString(count);
    }

    /**
     * Increment the counter for a key, or refuse to create a new one once the
     * map is at its ceiling. The capacity check races benignly: two threads
     * may both insert past the cap, which is why it is a ceiling rather than
     * an invariant.
     * @param counts the counter map
     * @param id the key to count
     * @return the new count, or {@link #UNTRACKED} when the key is not tracked
     */
    private static int increment(Map<Long, AtomicInteger> counts, Long id) {
        AtomicInteger existing = counts.get(id);
        if (existing != null) {
            return existing.incrementAndGet();
        }
        if (counts.size() >= MAX_TRACKED_KEYS) {
            return UNTRACKED;
        }
        return counts.computeIfAbsent(id, key -> new AtomicInteger()).incrementAndGet();
    }
}
