package com.example.posapp.entity;

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
 * Enforcement row materializing an employee's PIN claim at a single store.
 * <p>
 * One row exists per (assigned store) for every {@link Employee} that
 * currently holds a PIN: the row ties an {@link Employee} and a
 * {@link Store} to the employee's {@code pinFingerprint}. It is the
 * database-level realization of the store-scoped uniqueness rule and is
 * derived from, never a replacement for, the
 * {@link EmployeeStoreAssignment} relationship (the {@code fk_epss_assignment}
 * foreign key added by the Flyway V16 migration points this row back at the
 * matching assignment).
 * </p>
 * <p>
 * Two named constraints back the guarantees:
 * <ul>
 *   <li>{@code uk_epss_store_fingerprint} on (store_id, pin_fingerprint) is
 *       the core rule: no two employees assigned to the same store may
 *       claim the same PIN. An employee across several stores repeats their
 *       fingerprint on rows with different store ids, so one PIN still works
 *       at every assigned store.</li>
 *   <li>{@code uk_epss_employee_store} on (employee_id, store_id) keeps a
 *       single claim row per employee-store, which also makes re-adding an
 *       existing assignment idempotent.</li>
 * </ul>
 * This entity is manipulated only by the PIN writer and is never exposed as
 * a collection on the {@link Employee} or {@link Store} aggregates.
 * </p>
 */
@Entity
@Table(name = "employee_pin_store_scope",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_epss_store_fingerprint",
                        columnNames = {"store_id", "pin_fingerprint"}),
                @UniqueConstraint(name = "uk_epss_employee_store",
                        columnNames = {"employee_id", "store_id"})
        })
public class EmployeePinStoreScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The employee whose PIN is claimed at the store. Backed by the
     * {@code fk_epss_employee} foreign key.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_epss_employee"))
    private Employee employee;

    /**
     * The store the claim applies to. Backed by the {@code fk_epss_store}
     * foreign key.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "store_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_epss_store"))
    private Store store;

    /**
     * The employee's deterministic PIN fingerprint. Sensitive; never
     * returned by the API.
     */
    @Column(name = "pin_fingerprint", nullable = false, length = 128)
    private String pinFingerprint;

    /**
     * Default constructor for EmployeePinStoreScope.
     */
    public EmployeePinStoreScope() {}

    /**
     * Constructor for EmployeePinStoreScope.
     * @param employee the employee whose PIN is claimed
     * @param store the store the claim applies to
     * @param pinFingerprint the employee's deterministic PIN fingerprint
     */
    public EmployeePinStoreScope(Employee employee, Store store, String pinFingerprint) {
        this.employee = employee;
        this.store = store;
        this.pinFingerprint = pinFingerprint;
    }

    /**
     * Get the scope row ID.
     * @return the ID
     */
    public Long getId() { return id; }

    /**
     * Get the employee side of the claim.
     * @return the employee
     */
    public Employee getEmployee() { return employee; }

    /**
     * Set the employee side of the claim.
     * @param employee the employee
     */
    public void setEmployee(Employee employee) { this.employee = employee; }

    /**
     * Get the store side of the claim.
     * @return the store
     */
    public Store getStore() { return store; }

    /**
     * Set the store side of the claim.
     * @param store the store
     */
    public void setStore(Store store) { this.store = store; }

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
     * Return a string representation that omits the fingerprint so
     * accidental logging never exposes the sensitive value.
     * @return a redacted string representation
     */
    @Override
    public String toString() {
        return "EmployeePinStoreScope{id=" + id
                + ", employeeId=" + (employee == null ? null : employee.getId())
                + ", storeId=" + (store == null ? null : store.getId()) + "}";
    }
}
