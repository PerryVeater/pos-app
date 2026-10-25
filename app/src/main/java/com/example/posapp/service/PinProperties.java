package com.example.posapp.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the employee POS PIN credential feature, bound from
 * the {@code pos.pin.*} properties in {@code application.properties}.
 * <p>
 * The record pins the five knobs the {@link PinHasher},
 * {@link EmployeePinService} and {@link PinSecretGuard} need: the accepted
 * PIN length window, the PBKDF2 work factor, the HMAC secret that makes PIN
 * fingerprints deterministic within one deployment, and whether a changed
 * secret is being applied deliberately. Every field carries a safe default
 * so the context still starts with no {@code pos.pin.*} entries at all
 * (notably in tests); production deployments are expected to override
 * {@code hmacSecret} through the environment.
 * </p>
 *
 * @param minLength inclusive minimum PIN length in digits (default 4)
 * @param maxLength inclusive maximum PIN length in digits (default 6)
 * @param hmacSecret secret key for the deterministic PIN fingerprint;
 *                   must be overridden outside local development
 * @param pbkdf2Iterations PBKDF2 work factor for the stored PIN hash
 * @param fingerprintReset when {@code true}, a secret that does not match
 *                   the database's anchor is applied deliberately: every
 *                   store claim is dropped and employees must set their
 *                   PIN again. Off by default; see {@link PinSecretGuard}.
 */
@ConfigurationProperties(prefix = "pos.pin")
public record PinProperties(
        int minLength,
        int maxLength,
        String hmacSecret,
        int pbkdf2Iterations,
        boolean fingerprintReset) {

    /**
     * Compact constructor applying the documented defaults so an
     * environment with no {@code pos.pin.*} properties still produces a
     * usable configuration.
     */
    public PinProperties {
        if (minLength <= 0) {
            minLength = 4;
        }
        if (maxLength <= 0) {
            maxLength = 6;
        }
        if (hmacSecret == null || hmacSecret.isBlank()) {
            hmacSecret = "dev-only-pin-fingerprint-secret-change-me";
        }
        if (pbkdf2Iterations <= 0) {
            pbkdf2Iterations = 120_000;
        }
    }
}
