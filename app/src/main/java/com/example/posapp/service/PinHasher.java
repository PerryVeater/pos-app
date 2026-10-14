package com.example.posapp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Cryptographic helper for employee POS PIN credentials.
 * <p>
 * A PIN is a short numeric secret, so it is never persisted or returned in
 * plaintext. The hasher derives two values from the PIN string (leading
 * zeros preserved; the string is used verbatim with no trimming or
 * normalization):
 * <ul>
 *   <li>{@link #hash(String)} - a salted PBKDF2-HMAC-SHA256 password hash
 *       in the self-describing {@code pbkdf2:iterations$base64salt$base64hash}
 *       format, suitable for later verification via
 *       {@link #verify(String, String)} with a constant-time comparison.</li>
 *   <li>{@link #fingerprint(String)} - a deterministic HMAC-SHA256 keyed
 *       hash of the PIN. Because equality of two PINs must be detectable
 *       across rows (the store-scoped uniqueness rule), a salted random
 *       hash can never be compared for equality; the fingerprint fills
 *       that role. It is a keyed hash, not plaintext, and is treated as
 *       sensitive material that must never leave the API.</li>
 * </ul>
 * Both the PBKDF2 salt and the HMAC key come from {@link PinProperties};
 * the HMAC secret must be stable within a deployment because fingerprints
 * are persisted and compared. Login, sessions, and token issuance are out
 * of scope: this class only protects stored PIN material.
 * </p>
 */
@Component
public class PinHasher {

    /** Algorithm shared by the PBE key derivation and the stored hash format. */
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    /** Marker prefix identifying the stored hash format. */
    private static final String HASH_PREFIX = "pbkdf2:";
    /** Separator between the iteration prefix and the salt/hash pair. */
    private static final String HASH_SEPARATOR = "$";
    /** Byte length of the random per-hash salt. */
    private static final int SALT_BYTES = 16;
    /** Bit length of the derived PBKDF2 hash. */
    private static final int HASH_BITS = 256;
    /** JCA name of the keyed hash used for fingerprints. */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final PinProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Constructor for PinHasher.
     * @param properties the PIN feature configuration
     */
    public PinHasher(PinProperties properties) {
        this.properties = properties;
    }

    /**
     * Derive a salted PBKDF2 hash of the PIN string. Each call uses a
     * fresh random salt, so hashing the same PIN twice yields different
     * values that {@link #verify(String, String)} both accept.
     * @param pin the PIN as a digit string, verbatim
     * @return the self-describing hash
     *         {@code pbkdf2:iterations$base64salt$base64hash}
     */
    public String hash(String pin) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        int iterations = properties.pbkdf2Iterations();
        byte[] derived = pbkdf2(pin, salt, iterations);
        Base64.Encoder encoder = Base64.getEncoder();
        return HASH_PREFIX + iterations + HASH_SEPARATOR
                + encoder.encodeToString(salt) + HASH_SEPARATOR
                + encoder.encodeToString(derived);
    }

    /**
     * Verify a candidate PIN against a stored hash using a constant-time
     * comparison of the derived key material.
     * @param pin the candidate PIN string
     * @param storedHash the stored hash produced by {@link #hash(String)}
     * @return {@code true} when the PIN matches the hash
     */
    public boolean verify(String pin, String storedHash) {
        if (pin == null || storedHash == null || !storedHash.startsWith(HASH_PREFIX)) {
            return false;
        }
        String[] parts = storedHash.substring(HASH_PREFIX.length()).split("\\$");
        if (parts.length != 3) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[0]);
            Base64.Decoder decoder = Base64.getDecoder();
            byte[] salt = decoder.decode((String) parts[1]);
            byte[] expected = decoder.decode((String) parts[2]);
            byte[] actual = pbkdf2(pin, salt, iterations);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Derive the deterministic fingerprint of the PIN string: HMAC-SHA256
     * under the configured secret, lowercase hex. Two identical PINs always
     * produce the same fingerprint within a deployment; nothing but the
     * secret holder can invert it, so it is a keyed hash and not plaintext.
     * @param pin the PIN as a digit string, verbatim
     * @return the 64-character lowercase hex fingerprint
     */
    public String fingerprint(String pin) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.hmacSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(pin.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
            // HmacSHA256 is mandated by the JCA spec and a UTF-8 key is
            // always valid, so these only happen on a broken JVM.
            throw new IllegalStateException("Unable to compute PIN fingerprint", ex);
        }
    }

    /**
     * Run PBKDF2 over the PIN with the given salt and work factor.
     * @param pin the PIN string, used verbatim as the PBE password
     * @param salt the per-hash salt
     * @param iterations the work factor from configuration
     * @return the derived key bytes
     */
    private static byte[] pbkdf2(String pin, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, HASH_BITS);
            return SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("Unable to hash PIN credential", ex);
        }
    }
}
