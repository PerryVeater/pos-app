package com.example.posapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Credential entity holding a single employee's POS PIN secret.
 * <p>
 * The credential is stored separately from the {@link Employee} profile so
 * ordinary employee reads never carry PIN material. Two derived values are
 * persisted and neither is ever returned over the API or logged:
 * <ul>
 *   <li>{@code pinHash} - a salted password hash of the PIN, produced by
 *       the project's hasher, used to verify a supplied PIN.</li>
 *   <li>{@code pinFingerprint} - a deterministic keyed hash of the PIN,
 *       used only to detect two employees sharing the same PIN at a common
 *       store. It is treated as sensitive and is not plaintext.</li>
 * </ul>
 * An employee has at most one credential: {@code uk_employee_pin_credential_employee}
 * pins {@code employee_id} to uniqueness, matching the
 * {@code fk_epc_employee} foreign key created by the Flyway V16 migration.
 * Removing the credential (deleting this row) frees the employee's PIN
 * claims; the matching {@link EmployeePinStoreScope} rows are managed by the
 * writer and released together with it. Login, sessions, tokens, roles, and
 * permissions are intentionally out of scope for this layer.
 * </p>
 */
@Entity
@Table(name = "employee_pin_credential",
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_pin_credential_employee",
                columnNames = {"employee_id"}))
public class EmployeePinCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The employee this credential belongs to. Required, one-to-one, and
     * backed by the {@code fk_epc_employee} foreign key.
     */
    @OneToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_epc_employee"))
    private Employee employee;

    /**
     * Salted password hash of the PIN. Never plaintext; never returned by
     * the API.
     */
    @Column(name = "pin_hash", nullable = false, length = 255)
    private String pinHash;

    /**
     * Deterministic keyed hash of the PIN used solely for collision
     * detection. Sensitive but not plaintext; never returned by the API.
     */
    @Column(name = "pin_fingerprint", nullable = false, length = 128)
    private String pinFingerprint;

    /**
     * Default constructor for EmployeePinCredential.
     */
    public EmployeePinCredential() {}

    /**
     * Constructor for EmployeePinCredential.
     * @param employee the owning employee
     * @param pinHash the salted password hash of the PIN
     * @param pinFingerprint the deterministic keyed hash of the PIN
     */
    public EmployeePinCredential(Employee employee, String pinHash, String pinFingerprint) {
        this.employee = employee;
        this.pinHash = pinHash;
        this.pinFingerprint = pinFingerprint;
    }

    /**
     * Get the credential ID.
     * @return the ID
     */
    public Long getId() { return id; }

    /**
     * Get the owning employee.
     * @return the employee
     */
    public Employee getEmployee() { return employee; }

    /**
     * Set the owning employee.
     * @param employee the employee
     */
    public void setEmployee(Employee employee) { this.employee = employee; }

    /**
     * Get the salted PIN hash.
     * @return the password hash
     */
    public String getPinHash() { return pinHash; }

    /**
     * Set the salted PIN hash.
     * @param pinHash the password hash
     */
    public void setPinHash(String pinHash) { this.pinHash = pinHash; }

    /**
     * Get the PIN fingerprint.
     * @return the deterministic keyed hash
     */
    public String getPinFingerprint() { return pinFingerprint; }

    /**
     * Set the PIN fingerprint.
     * @param pinFingerprint the deterministic keyed hash
     */
    public void setPinFingerprint(String pinFingerprint) { this.pinFingerprint = pinFingerprint; }

    /**
     * Return a string representation that deliberately omits the secret
     * material so accidental logging never exposes the hash or fingerprint.
     * @return a redacted string representation
     */
    @Override
    public String toString() {
        return "EmployeePinCredential{id=" + id
                + ", employeeId=" + (employee == null ? null : employee.getId()) + "}";
    }
}
