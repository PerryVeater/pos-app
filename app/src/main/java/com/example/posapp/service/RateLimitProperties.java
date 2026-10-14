package com.example.posapp.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for PIN-authentication rate limiting, bound from the
 * {@code pos.auth.rate-limit.*} properties in {@code application.properties}.
 * <p>
 * The limiter is a per-{@code (store, client)} sliding failure counter with
 * a cooldown, intended to blunt offline PIN-guessing from a single terminal
 * or IP. Its shape:
 * <ul>
 *   <li>{@code maxAttempts} - the number of failed attempts tolerated within
 *       one {@code window} before the client is locked out;</li>
 *   <li>{@code window} - the rolling period over which failures are counted
 *       (a gap longer than the window resets the count);</li>
 *   <li>{@code cooldown} - how long a locked-out {@code (store, client)}
 *       pair stays blocked after the limit is breached;</li>
 *   <li>{@code trustForwardedFor} - whether to read the client identifier
 *       from {@code X-Forwarded-For}. Defaults to {@code false}: the remote
 *       address is used so a client cannot spoof the header to escape the
 *       limit. Enable only when the app is known to sit behind a trusted
 *       reverse proxy that overwrites the header;</li>
 *   <li>{@code maxEntries} - a hard cap on how many {@code (store, client)}
 *       buckets the in-memory map may hold, so arbitrary client input can
 *       never grow it without bound; the oldest-tracked entries are evicted
 *       once the cap is reached.</li>
 * </ul>
 * Every field carries a safe default so the context starts with no
 * {@code pos.auth.rate-limit.*} entries present (notably in tests).
 * </p>
 *
 * @param maxAttempts failures allowed per window before lockout (default 5)
 * @param window rolling window over which failures count (default 5 minutes)
 * @param cooldown lockout duration after the limit is hit (default 15 minutes)
 * @param trustForwardedFor whether to derive the client id from
 *        {@code X-Forwarded-For} (default false, use the remote address)
 * @param maxEntries hard ceiling on tracked {@code (store, client)} buckets
 *        (default 100000)
 */
@ConfigurationProperties(prefix = "pos.auth.rate-limit")
public record RateLimitProperties(
        int maxAttempts,
        Duration window,
        Duration cooldown,
        boolean trustForwardedFor,
        int maxEntries) {

    /**
     * Compact constructor applying documented defaults for any missing or
     * non-positive value.
     */
    public RateLimitProperties {
        if (maxAttempts <= 0) {
            maxAttempts = 5;
        }
        if (window == null || window.isZero() || window.isNegative()) {
            window = Duration.ofMinutes(5);
        }
        if (cooldown == null || cooldown.isZero() || cooldown.isNegative()) {
            cooldown = Duration.ofMinutes(15);
        }
        if (maxEntries <= 0) {
            maxEntries = 100_000;
        }
    }
}
