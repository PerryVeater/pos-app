package com.example.posapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PosSessionTokenService}: token generation is
 * opaque, high-entropy, and unique; hashing is deterministic, hex-encoded,
 * and one-way (the stored digest never equals or contains the raw token).
 */
class PosSessionTokenServiceTest {

    private final PosSessionTokenService service = new PosSessionTokenService();

    @Test
    @DisplayName("generated tokens are URL-safe, decode back to 32 bytes, and are unique")
    void generatedTokensAreOpaqueAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String token = service.generateToken();
            assertThat(token).isNotBlank();
            // URL-safe, unpadded alphabet only.
            assertThat(token).matches("[A-Za-z0-9_-]+");
            // Decodes to the full 256 bits of entropy.
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
            seen.add(token);
        }
        assertThat(seen).hasSize(100);
    }

    @Test
    @DisplayName("hash is deterministic 64-char lowercase hex")
    void hashIsDeterministicHex() {
        String token = service.generateToken();
        String first = service.hash(token);
        String second = service.hash(token);

        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("the stored hash never equals or reveals the raw token")
    void hashDoesNotLeakRawToken() {
        String token = service.generateToken();
        String hash = service.hash(token);

        assertThat(hash).isNotEqualTo(token);
        assertThat(hash).doesNotContain(token);
    }

    @Test
    @DisplayName("distinct tokens hash to distinct digests")
    void distinctTokensDistinctHashes() {
        String a = service.generateToken();
        String b = service.generateToken();

        assertThat(service.hash(a)).isNotEqualTo(service.hash(b));
    }
}
