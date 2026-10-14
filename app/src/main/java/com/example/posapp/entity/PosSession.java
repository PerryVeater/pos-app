package com.example.posapp.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A temporary, store-scoped POS bearer session issued when an
 * {@link Employee} authenticates at a {@link Store} with their PIN.
 * <p>
 * The session is deliberately credential-free: it stores only a
 * cryptographic {@code tokenHash} (the SHA-256 hex digest of the opaque
 * bearer token handed to the client once at issuance), never the raw token
 * and never the PIN or any PIN material. The raw token is not even a column
 * here - the API contract guarantees it exists only in the authentication
 * response body.
 * </p>
 * <p>
 * A session is scoped to the exact {@code employee}/{@code store} pair that
 * authenticated, matching the {@code fk_pos_session_employee} and
 * {@code fk_pos_session_store} foreign keys created by the Flyway V17
 * migration; both cascade on delete so a session never blocks removal of the
 * aggregates it points at. Its usability is time- and revocation-bounded:
 * live while {@code revokedAt} is {@code null} and {@code now} precedes
 * {@code expiresAt} (see {@link #isLive(Instant)}).
 * </p>
 */
@Entity
@Table(name = "pos_session",
        uniqueConstraints = @UniqueConstraint(name = "uk_pos_session_token_hash",
                columnNames = {"token_hash"}))
public class PosSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * SHA-256 hex digest of the raw bearer token. Unique via
     * {@code uk_pos_session_token_hash}; this is the only token-derived
     * value that is ever persisted and it must never appear in a response
     * or log.
     */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /**
     * The authenticated employee. Required; backed by
     * {@code fk_pos_session_employee}.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pos_session_employee"))
    private Employee employee;

    /**
     * The store the session was opened at. Required; backed by
     * {@code fk_pos_session_store}.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "store_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pos_session_store"))
    private Store store;

    /**
     * Issuance instant (UTC). Set once when the session is created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Absolute expiry instant (UTC): {@code createdAt} plus the configured
     * session lifetime. A session is unusable at or after this instant.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Revocation marker. {@code null} while the session is live; stamped
     * with the logout instant once revoked. Kept rather than deleted so
     * logout history is auditable and repeated logout stays idempotent.
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Default constructor for PosSession.
     */
    public PosSession() {}

    /**
     * Constructor for PosSession capturing the values fixed at issuance.
     * The revocation marker starts unset (live).
     * @param employee the authenticated employee
     * @param store the store the session was opened at
     * @param tokenHash the SHA-256 hex digest of the raw bearer token
     * @param createdAt the issuance instant
     * @param expiresAt the absolute expiry instant
     */
    public PosSession(Employee employee, Store store, String tokenHash,
                      Instant createdAt, Instant expiresAt) {
        this.employee = employee;
        this.store = store;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Whether the session is usable at the given instant: not revoked and
     * not yet expired.
     * @param now the reference instant (UTC)
     * @return {@code true} when the session is still live
     */
    public boolean isLive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Get the session ID.
     * @return the ID
     */
    public Long getId() { return id; }

    /**
     * Get the stored token hash. Sensitive; never serialized into an API
     * response.
     * @return the SHA-256 hex digest of the bearer token
     */
    public String getTokenHash() { return tokenHash; }

    /**
     * Set the stored token hash.
     * @param tokenHash the SHA-256 hex digest of the bearer token
     */
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    /**
     * Get the authenticated employee.
     * @return the employee
     */
    public Employee getEmployee() { return employee; }

    /**
     * Set the authenticated employee.
     * @param employee the employee
     */
    public void setEmployee(Employee employee) { this.employee = employee; }

    /**
     * Get the store the session was opened at.
     * @return the store
     */
    public Store getStore() { return store; }

    /**
     * Set the store the session was opened at.
     * @param store the store
     */
    public void setStore(Store store) { this.store = store; }

    /**
     * Get the issuance instant.
     * @return the created_at instant
     */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * Set the issuance instant.
     * @param createdAt the created_at instant
     */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /**
     * Get the expiry instant.
     * @return the expires_at instant
     */
    public Instant getExpiresAt() { return expiresAt; }

    /**
     * Set the expiry instant.
     * @param expiresAt the expires_at instant
     */
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    /**
     * Get the revocation marker.
     * @return the revocation instant, or {@code null} while live
     */
    public Instant getRevokedAt() { return revokedAt; }

    /**
     * Set the revocation marker.
     * @param revokedAt the revocation instant, or {@code null} to reactivate
     */
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }

    /**
     * Return a string representation that deliberately omits the token hash
     * so accidental logging never exposes session material.
     * @return a redacted string representation
     */
    @Override
    public String toString() {
        return "PosSession{id=" + id
                + ", employeeId=" + (employee == null ? null : employee.getId())
                + ", storeId=" + (store == null ? null : store.getId())
                + ", createdAt=" + createdAt
                + ", expiresAt=" + expiresAt
                + ", revokedAt=" + revokedAt + "}";
    }
}
