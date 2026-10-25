package com.example.posapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PinHasher}.
 * <p>
 * These lock the two security properties the credential layer relies on:
 * the salted hash verifies a matching PIN and rejects a non-matching one
 * while never containing the plaintext, and the fingerprint is
 * deterministic (equal PINs collide, different PINs do not) so the
 * store-scoped uniqueness rule can detect shared PINs without storing the
 * PIN itself. A deliberately low PBKDF2 iteration count keeps the tests
 * fast; correctness is independent of the work factor.
 * </p>
 */
class PinHasherTest {

    private final PinHasher hasher =
            new PinHasher(new PinProperties(4, 6, "test-hmac-secret", 1000, false));

    @Test
    @DisplayName("hash verifies the same PIN and rejects a different one")
    void hashVerifiesMatchingPinOnly() {
        String hash = hasher.hash("0421");

        assertThat(hasher.verify("0421", hash)).isTrue();
        assertThat(hasher.verify("1421", hash)).isFalse();
    }

    @Test
    @DisplayName("each hash uses a fresh salt so the same PIN yields different digests")
    void hashingIsSaltedPerCall() {
        String first = hasher.hash("0421");
        String second = hasher.hash("0421");

        assertThat(first).isNotEqualTo(second);
        assertThat(hasher.verify("0421", first)).isTrue();
        assertThat(hasher.verify("0421", second)).isTrue();
    }

    @Test
    @DisplayName("the stored hash never contains the plaintext PIN")
    void hashDoesNotContainPlaintext() {
        String pin = "0421";

        assertThat(hasher.hash(pin)).doesNotContain(pin);
    }

    @Test
    @DisplayName("verify rejects malformed or null stored hashes without throwing")
    void verifyRejectsGarbageInput() {
        assertThat(hasher.verify("0421", null)).isFalse();
        assertThat(hasher.verify(null, "not-a-hash")).isFalse();
        assertThat(hasher.verify("0421", "not-a-hash")).isFalse();
        assertThat(hasher.verify("0421", "pbkdf2:1000$onlytwoparts")).isFalse();
    }

    @Test
    @DisplayName("fingerprint is deterministic: equal PINs share a fingerprint, different PINs differ")
    void fingerprintIsDeterministicAndCollisionFreeForDistinctPins() {
        assertThat(hasher.fingerprint("0421")).isEqualTo(hasher.fingerprint("0421"));
        assertThat(hasher.fingerprint("0421")).isNotEqualTo(hasher.fingerprint("1421"));
    }

    @Test
    @DisplayName("leading-zero PINs have distinct fingerprints from their trimmed equivalents")
    void fingerprintPreservesLeadingZeros() {
        assertThat(hasher.fingerprint("0421")).isNotEqualTo(hasher.fingerprint("421"));
    }

    @Test
    @DisplayName("the fingerprint never reveals the plaintext PIN")
    void fingerprintDoesNotContainPlaintext() {
        assertThat(hasher.fingerprint("0421")).doesNotContain("0421");
    }
}
