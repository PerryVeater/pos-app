package com.example.posapp.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for POS authentication sessions, bound from the
 * {@code pos.session.*} properties in {@code application.properties}.
 * <p>
 * Three knobs, all with defaults so the context still starts with no
 * {@code pos.session.*} entries present (notably in tests):
 * <ul>
 *   <li>{@code ttl} - absolute lifetime of a session from issuance;</li>
 *   <li>{@code maxPerEmployee} - how many sessions one employee may hold
 *       at once. A terminal that crashed without logging out must not keep
 *       a session alive forever, so the oldest live session is revoked when
 *       the next one is issued beyond the cap;</li>
 *   <li>{@code retention} - how long an expired row is kept before the
 *       purge job removes it. Every row carries an absolute expiry, so
 *       revoked rows are covered by the same rule; retention is the audit
 *       window, not a second condition.</li>
 * </ul>
 * {@code purgeBatchSize} bounds a single purge transaction. Durations are
 * {@link Duration}s, so Spring accepts ISO-8601 or shorthand such as
 * {@code 8h} and {@code 7d}.
 * </p>
 *
 * @param ttl session lifetime measured from issuance (default 8 hours)
 * @param maxPerEmployee concurrent live sessions allowed per employee
 *        (default 3; values below 1 fall back to the default)
 * @param retention how long an expired session row is kept before purge
 *        (default 7 days)
 * @param purgeBatchSize maximum rows removed in one purge transaction
 *        (default 500; values below 1 fall back to the default)
 */
@ConfigurationProperties(prefix = "pos.session")
public record SessionProperties(
        Duration ttl,
        int maxPerEmployee,
        Duration retention,
        int purgeBatchSize) {

    /**
     * Compact constructor applying the documented defaults to anything
     * absent or nonsensical, so a misconfigured deployment degrades to a
     * working policy rather than an unbounded or instantly-expiring one.
     */
    public SessionProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            ttl = Duration.ofHours(8);
        }
        if (maxPerEmployee < 1) {
            maxPerEmployee = 3;
        }
        if (retention == null || retention.isNegative()) {
            retention = Duration.ofDays(7);
        }
        if (purgeBatchSize < 1) {
            purgeBatchSize = 500;
        }
    }
}
