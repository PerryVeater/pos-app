package com.example.posapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PosAuthRateLimiter} driven by a controllable clock so
 * window rolls, cooldown expiry, and the bounded-memory ceiling are asserted
 * deterministically rather than by sleeping.
 */
class PosAuthRateLimiterTest {

    private static final long STORE = 7L;
    private static final String CLIENT = "203.0.113.9";

    private final AtomicLong now = new AtomicLong(1_000L);
    private final PosAuthRateLimiter limiter = new PosAuthRateLimiter(
            new RateLimitProperties(3, Duration.ofMinutes(5), Duration.ofMinutes(15), false, 1_000),
            now::get);

    @Test
    @DisplayName("a fresh client is not blocked")
    void freshClientIsOpen() {
        assertThat(limiter.isBlocked(STORE, CLIENT)).isFalse();
    }

    @Test
    @DisplayName("attempts within the limit are allowed; the limit-th failure locks out")
    void blocksOnceLimitReached() {
        limiter.recordFailure(STORE, CLIENT);
        assertThat(limiter.isBlocked(STORE, CLIENT)).as("1 failure").isFalse();

        limiter.recordFailure(STORE, CLIENT);
        assertThat(limiter.isBlocked(STORE, CLIENT)).as("2 failures").isFalse();

        limiter.recordFailure(STORE, CLIENT); // 3rd reaches maxAttempts
        assertThat(limiter.isBlocked(STORE, CLIENT)).as("3 failures -> cooldown").isTrue();
    }

    @Test
    @DisplayName("only the failure that begins a lockout reports the transition")
    void lockoutEntryIsReportedOnce() {
        assertThat(limiter.recordFailure(STORE, CLIENT)).as("1st").isFalse();
        assertThat(limiter.recordFailure(STORE, CLIENT)).as("2nd").isFalse();
        assertThat(limiter.recordFailure(STORE, CLIENT)).as("3rd begins the lockout").isTrue();
        // Refusals while the lockout holds must not re-announce it, or a
        // client hammering the endpoint would flood the alert channel.
        assertThat(limiter.recordFailure(STORE, CLIENT)).as("4th, already locked").isFalse();
    }

    @Test
    @DisplayName("a new lockout after the cooldown lapsed is reported again")
    void lockoutAfterCooldownIsReportedAgain() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure(STORE, CLIENT);
        }
        now.set(now.get() + Duration.ofMinutes(15).toMillis() + 1);

        limiter.recordFailure(STORE, CLIENT);
        limiter.recordFailure(STORE, CLIENT);
        assertThat(limiter.recordFailure(STORE, CLIENT)).as("new lockout").isTrue();
    }

    @Test
    @DisplayName("the lockout lifts once the cooldown elapses")
    void lockoutExpiresAfterCooldown() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure(STORE, CLIENT);
        }
        assertThat(limiter.isBlocked(STORE, CLIENT)).isTrue();

        now.set(now.get() + Duration.ofMinutes(15).toMillis() + 1);
        assertThat(limiter.isBlocked(STORE, CLIENT)).as("cooldown elapsed").isFalse();
    }

    @Test
    @DisplayName("a success clears the counter so past mistakes do not linger")
    void successResetsBucket() {
        limiter.recordFailure(STORE, CLIENT);
        limiter.recordFailure(STORE, CLIENT);
        limiter.recordSuccess(STORE, CLIENT);

        // Two more failures after a reset stay under the limit of three.
        limiter.recordFailure(STORE, CLIENT);
        limiter.recordFailure(STORE, CLIENT);
        assertThat(limiter.isBlocked(STORE, CLIENT)).isFalse();
    }

    @Test
    @DisplayName("failures spaced beyond the window do not accumulate")
    void windowRollResetsCount() {
        limiter.recordFailure(STORE, CLIENT);
        // Jump past the rolling window before the next failure.
        now.set(now.get() + Duration.ofMinutes(5).toMillis() + 1);
        limiter.recordFailure(STORE, CLIENT);
        limiter.recordFailure(STORE, CLIENT);

        // Each post-roll burst restarts at one, so three total but never three
        // inside a single window: still not blocked.
        assertThat(limiter.isBlocked(STORE, CLIENT)).isFalse();
    }

    @Test
    @DisplayName("distinct stores and clients get independent buckets")
    void bucketsAreScopedPerStoreAndClient() {
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure(STORE, CLIENT);
        }
        assertThat(limiter.isBlocked(STORE, CLIENT)).isTrue();
        assertThat(limiter.isBlocked(STORE + 1, CLIENT)).as("other store").isFalse();
        assertThat(limiter.isBlocked(STORE, "198.51.100.4")).as("other client").isFalse();
    }

    @Test
    @DisplayName("the tracked bucket count never exceeds maxEntries")
    void memoryIsBounded() {
        PosAuthRateLimiter capped = new PosAuthRateLimiter(
                new RateLimitProperties(5, Duration.ofMinutes(5), Duration.ofMinutes(15), false, 3),
                now::get);
        for (int i = 0; i < 50; i++) {
            capped.recordFailure(STORE, "client-" + i);
        }
        assertThat(capped.trackedEntries()).isLessThanOrEqualTo(3);
    }
}
