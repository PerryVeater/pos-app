package com.example.posapp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

/**
 * Issues and fingerprints the opaque bearer tokens that back POS sessions.
 * <p>
 * A session token is a high-entropy, cryptographically random value - it is
 * <em>not</em> derived from the employee PIN in any way, so a leaked or
 * guessed PIN never yields a token and a leaked token never reveals a PIN.
 * Because the token is random and long (256 bits of entropy), it is stored
 * as a fast SHA-256 digest rather than a slow password hash: the digest is
 * only ever used for exact equality lookup, and an attacker who stole the
 * table could not realistically invert a 256-bit random value even without
 * a work factor. The raw token is produced once, returned to the client a
 * single time, and never persisted.
 * </p>
 */
@Component
public class PosSessionTokenService {

    /** Bytes of CSPRNG entropy per token (256 bits). */
    private static final int TOKEN_BYTES = 32;
    /** URL-safe, unpadded encoder keeps the token transport-friendly. */
    private static final Base64.Encoder TOKEN_ENCODER =
            Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a fresh opaque bearer token. Each call draws new entropy from
     * a {@link SecureRandom}, so tokens are unguessable and unique for all
     * practical purposes.
     * @return a URL-safe Base64 token string (no padding)
     */
    public String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return TOKEN_ENCODER.encodeToString(bytes);
    }

    /**
     * Derive the value stored in {@code pos_session.token_hash}: the SHA-256
     * digest of the raw token rendered as lowercase hex. Deterministic so a
     * presented token can be matched to its stored hash, but irreversible,
     * so the stored digest never exposes the usable token.
     * @param rawToken the raw bearer token handed to a client
     * @return the 64-character lowercase hex SHA-256 digest
     */
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JCA spec, so this only happens on a
            // broken JVM.
            throw new IllegalStateException("Unable to hash session token", ex);
        }
    }
}
