package com.example.posapp.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for POS authentication sessions, bound from the
 * {@code pos.session.*} properties in {@code application.properties}.
 * <p>
 * The only knob is the session {@code ttl}: how long a freshly issued
 * bearer session stays valid before it expires and must be re-authenticated.
 * It is an absolute lifetime measured from issuance, expressed as a
 * {@link Duration} (Spring binds ISO-8601 or shorthand such as {@code 8h}).
 * The compact constructor applies a sensible 8-hour default so the context
 * still starts with no {@code pos.session.*} entries present (notably in
 * tests).
 * </p>
 *
 * @param ttl session lifetime measured from issuance (default 8 hours)
 */
@ConfigurationProperties(prefix = "pos.session")
public record SessionProperties(Duration ttl) {

    /**
     * Compact constructor applying the documented 8-hour default when the
     * lifetime is absent or non-positive.
     */
    public SessionProperties {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            ttl = Duration.ofHours(8);
        }
    }
}
