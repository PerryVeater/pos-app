package com.example.posapp.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The single row that records which {@code pos.pin.hmac-secret} this
 * database's PIN fingerprints were written under.
 * <p>
 * Fingerprints are keyed hashes, so a claim row is only meaningful under
 * the secret that produced it. The PINs themselves are not recoverable
 * (only salted PBKDF2 hashes are stored), which means a changed secret can
 * never re-materialize the existing claims - the claim has to be written
 * again, from a PIN somebody types. Detecting the change at startup is what
 * turns that into a refusal to boot instead of every employee's login
 * failing with the same generic 401 a wrong PIN produces.
 * </p>
 * <p>
 * There is exactly one row, with the fixed id {@link #SINGLETON_ID}
 * enforced by {@code ck_pps_single_row} from Flyway V21. {@code secretHash}
 * is a salted PBKDF2 hash of the secret, never the secret and never a bare
 * digest of it: a dumped table must not hand an attacker a cheap offline
 * oracle for guessing the key.
 * </p>
 */
@Entity
@Table(name = "pos_pin_secret_anchor")
public class PinSecretAnchor {

    /**
     * The only id this table may hold; the check constraint
     * {@code ck_pps_single_row} rejects anything else.
     */
    public static final Long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id", nullable = false)
    private Long id = SINGLETON_ID;

    /**
     * Salted PBKDF2 hash of the fingerprint secret, in the same
     * self-describing format {@link com.example.posapp.service.PinHasher}
     * uses for PINs. Never returned by the API and never logged.
     */
    @Column(name = "secret_hash", nullable = false, length = 255)
    private String secretHash;

    /**
     * When the current secret was first anchored, or last re-anchored after
     * a deliberate reset.
     */
    @Column(name = "anchored_at", nullable = false)
    private Instant anchoredAt;

    /**
     * Default constructor for PinSecretAnchor.
     */
    public PinSecretAnchor() {}

    /**
     * Constructor for PinSecretAnchor.
     * @param secretHash the salted hash of the configured secret
     * @param anchoredAt the anchoring instant
     */
    public PinSecretAnchor(String secretHash, Instant anchoredAt) {
        this.secretHash = secretHash;
        this.anchoredAt = anchoredAt;
    }

    /**
     * Get the anchor ID.
     * @return the ID, always {@link #SINGLETON_ID}
     */
    public Long getId() { return id; }

    /**
     * Get the salted hash of the anchored secret.
     * @return the secret hash
     */
    public String getSecretHash() { return secretHash; }

    /**
     * Replace the anchored secret hash.
     * @param secretHash the salted hash of the new secret
     */
    public void setSecretHash(String secretHash) { this.secretHash = secretHash; }

    /**
     * Get the anchoring instant.
     * @return the instant the current secret was anchored
     */
    public Instant getAnchoredAt() { return anchoredAt; }

    /**
     * Replace the anchoring instant.
     * @param anchoredAt the instant the new secret was anchored
     */
    public void setAnchoredAt(Instant anchoredAt) { this.anchoredAt = anchoredAt; }

    /**
     * Return a representation without the secret material, so accidental
     * logging can never expose the hash.
     * @return a redacted string representation
     */
    @Override
    public String toString() {
        return "PinSecretAnchor{id=" + id + ", anchoredAt=" + anchoredAt + "}";
    }
}
