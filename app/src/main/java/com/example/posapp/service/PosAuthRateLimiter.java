package com.example.posapp.service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * In-memory, process-local rate limiter for POS PIN authentication.
 * <p>
 * Failed attempts are counted per {@code (store, client)} bucket within a
 * rolling {@link RateLimitProperties#window() window}; once
 * {@link RateLimitProperties#maxAttempts() maxAttempts} failures accumulate
 * the bucket enters a {@link RateLimitProperties#cooldown() cooldown} during
 * which {@link #isBlocked} reports {@code true}. A successful authentication
 * clears the bucket so legitimate users never trip the limit through ordinary
 * use, and a gap longer than the window resets the counter so isolated
 * mistakes do not accumulate.
 * </p>
 * <p>
 * <b>Bounded memory:</b> the backing map is capped at
 * {@link RateLimitProperties#maxEntries() maxEntries} buckets. After any
 * insertion the limiter first drops buckets that are no longer enforcing
 * anything (out of lockout and out of window) and, if still over the cap,
 * evicts the least-recently-touched buckets. Arbitrary client input can
 * therefore never grow the structure without bound.
 * </p>
 * <p>
 * <b>Scope limitation:</b> state lives in this JVM only. It is correct for
 * the current single-instance deployment but is <em>not</em> shared across
 * multiple application instances - a horizontally scaled deployment would
 * need a shared store (for example Redis) to enforce a global limit. The
 * clock is injectable so tests can move time deterministically.
 * </p>
 */
@Component
public class PosAuthRateLimiter {

    /**
     * Immutable per-bucket snapshot. {@code windowStartEpochMs} anchors the
     * rolling window, {@code lockoutUntilEpochMs} is {@code 0} while the
     * bucket is not locked and a future instant while it is, and
     * {@code lastTouchedEpochMs} orders eviction.
     */
    private record Bucket(int failures, long windowStartEpochMs,
                          long lockoutUntilEpochMs, long lastTouchedEpochMs) {
    }

    private final RateLimitProperties properties;
    private final LongSupplier clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Production constructor: wall-clock milliseconds.
     * @param properties the rate-limit configuration
     */
    @Autowired
    public PosAuthRateLimiter(RateLimitProperties properties) {
        this(properties, System::currentTimeMillis);
    }

    /**
     * Test-visible constructor allowing a deterministic clock.
     * @param properties the rate-limit configuration
     * @param clock epoch-millisecond time source
     */
    PosAuthRateLimiter(RateLimitProperties properties, LongSupplier clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Whether the {@code (store, client)} pair is currently inside a cooldown.
     * Called before any credential work so a locked-out client is rejected
     * cheaply and identically regardless of the PIN supplied.
     * @param storeId the store being authenticated against
     * @param clientKey the resolved client identifier (e.g. remote IP)
     * @return {@code true} when further attempts must be refused
     */
    public boolean isBlocked(Long storeId, String clientKey) {
        Bucket bucket = buckets.get(key(storeId, clientKey));
        return bucket != null && clock.getAsLong() < bucket.lockoutUntilEpochMs();
    }

    /**
     * Register a failed attempt for the {@code (store, client)} pair, rolling
     * the window forward when needed and engaging the cooldown once the
     * configured attempt limit is reached.
     * @param storeId the store being authenticated against
     * @param clientKey the resolved client identifier
     * @return {@code true} only for the failure that began a lockout, so a
     *         caller can report the transition once instead of once per
     *         refused attempt afterwards
     */
    public boolean recordFailure(Long storeId, String clientKey) {
        long now = clock.getAsLong();
        long windowMillis = properties.window().toMillis();
        long cooldownMillis = properties.cooldown().toMillis();
        // compute() may retry under contention, so the previous state is
        // captured by the last invocation rather than accumulated.
        AtomicReference<Bucket> previous = new AtomicReference<>();
        Bucket updated = buckets.compute(key(storeId, clientKey), (k, existing) -> {
            previous.set(existing);
            if (existing == null) {
                existing = new Bucket(0, now, 0L, now);
            }
            if (now < existing.lockoutUntilEpochMs()) {
                // Already locked out: do not extend the lockout (that would
                // let continuous noise postpone the reset indefinitely), just
                // note the touch for eviction ordering.
                return new Bucket(existing.failures(), existing.windowStartEpochMs(),
                        existing.lockoutUntilEpochMs(), now);
            }
            int failures;
            long windowStart;
            if (now - existing.windowStartEpochMs() > windowMillis) {
                failures = 1;
                windowStart = now;
            } else {
                failures = existing.failures() + 1;
                windowStart = existing.windowStartEpochMs();
            }
            long lockoutUntil = failures >= properties.maxAttempts()
                    ? now + cooldownMillis
                    : 0L;
            return new Bucket(failures, windowStart, lockoutUntil, now);
        });
        enforceCapacity(now);
        Bucket before = previous.get();
        boolean wasOpen = before == null || now >= before.lockoutUntilEpochMs();
        return wasOpen && now < updated.lockoutUntilEpochMs();
    }

    /**
     * Clear the {@code (store, client)} bucket after a successful
     * authentication so prior mistakes do not linger.
     * @param storeId the store being authenticated against
     * @param clientKey the resolved client identifier
     */
    public void recordSuccess(Long storeId, String clientKey) {
        buckets.remove(key(storeId, clientKey));
    }

    /**
     * Number of buckets currently tracked. Exposed for tests and diagnostics.
     * @return the tracked-bucket count
     */
    int trackedEntries() {
        return buckets.size();
    }

    private static String key(Long storeId, String clientKey) {
        return storeId + ":" + clientKey;
    }

    /**
     * Keep the map within {@code maxEntries}: drop buckets that are neither
     * in lockout nor still inside their window, then, if still over the cap,
     * evict the least-recently-touched buckets. Only does real work when the
     * size has reached the ceiling.
     * @param now the current epoch-millisecond time
     */
    private void enforceCapacity(long now) {
        int cap = properties.maxEntries();
        if (buckets.size() <= cap) {
            return;
        }
        long windowMillis = properties.window().toMillis();
        // Pass 1: drop cold buckets (not locked, window elapsed).
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext() && buckets.size() > cap) {
            Bucket bucket = it.next().getValue();
            boolean locked = now < bucket.lockoutUntilEpochMs();
            boolean inWindow = now - bucket.windowStartEpochMs() <= windowMillis;
            if (!locked && !inWindow) {
                it.remove();
            }
        }
        // Pass 2: still full - evict oldest-touched buckets so a flood of new
        // client keys cannot push the structure past the cap.
        while (buckets.size() > cap) {
            String oldestKey = null;
            long oldestTouch = Long.MAX_VALUE;
            for (Map.Entry<String, Bucket> entry : buckets.entrySet()) {
                if (entry.getValue().lastTouchedEpochMs() < oldestTouch) {
                    oldestTouch = entry.getValue().lastTouchedEpochMs();
                    oldestKey = entry.getKey();
                }
            }
            if (oldestKey == null) {
                break;
            }
            buckets.remove(oldestKey);
        }
    }
}
