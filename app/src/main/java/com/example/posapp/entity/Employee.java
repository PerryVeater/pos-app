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

/**
 * Entity class representing an employee in the Point-of-Sale (POS) system.
 * <p>
 * An employee belongs to exactly one {@link Organization}; the
 * {@code organization} association is required and enforced by the
 * {@code fk_employee_organization} foreign key at the database level. An
 * employee carries its own identity fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the employee.</li>
 *   <li>{@code organization} - Required owning organization; matches the
 *       {@code fk_employee_organization} constraint.</li>
 *   <li>{@code name} - Required employee name; not unique.</li>
 *   <li>{@code email} - Optional contact email; unique within the owning
 *       organization when present.</li>
 *   <li>{@code active} - Whether the employee is currently in service.</li>
 * </ul>
 * </p>
 * <p>
 * Email uniqueness is enforced by the composite
 * {@code uk_employee_organization_email} constraint on
 * {@code (organization_id, email)}; PostgreSQL treats NULLs as distinct so
 * any number of employees in an organization may leave the email unset.
 * Emails are stored as submitted after trimming and are not case-folded,
 * matching the {@code stores.email} convention. Employee group membership,
 * permissions, inherited configuration, store assignments, and
 * authorization are intentionally out of scope for this foundation.
 * </p>
 */
@Entity
@Table(name = "employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning organization. Required: an employee cannot exist without one,
     * so the column is NOT NULL and the join column references
     * {@code organization(id)} through {@code fk_employee_organization}.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_employee_organization"))
    private Organization organization;

    /**
     * Required human-readable employee name. Not unique; two employees may
     * share the same name within or across organizations.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Optional contact email, unique within the owning organization when
     * present. {@code null} means "no email on file"; whitespace-only
     * input is normalized to {@code null} by the DTO and the service
     * before it reaches the entity.
     */
    @Column(length = 255)
    private String email;

    /**
     * Lifecycle state: whether the employee is currently in service.
     * Inactive employees are kept rather than deleted, and new employees
     * default to active.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Default constructor for Employee.
     */
    public Employee() {}

    /**
     * Constructor for Employee.
     * @param name the name of the employee
     */
    public Employee(String name) {
        this.name = name;
    }

    /**
     * Get the ID of the employee.
     * @return the ID of the employee
     */
    public Long getId() { return id; }

    /**
     * Get the owning organization.
     * @return the organization that owns this employee record
     */
    public Organization getOrganization() { return organization; }

    /**
     * Set the owning organization.
     * @param organization the organization that owns this employee record
     */
    public void setOrganization(Organization organization) { this.organization = organization; }

    /**
     * Get the name of the employee.
     * @return the name of the employee
     */
    public String getName() { return name; }

    /**
     * Set the name of the employee.
     * @param name the name of the employee
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get the optional email.
     * @return the email, or {@code null} when unset
     */
    public String getEmail() { return email; }

    /**
     * Set the optional email.
     * @param email the email, or {@code null} to clear
     */
    public void setEmail(String email) { this.email = email; }

    /**
     * Get whether the employee is in service.
     * @return {@code true} if the employee is currently active
     */
    public boolean isActive() { return active; }

    /**
     * Set whether the employee is in service.
     * @param active whether the employee is currently active
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Return a string representation of the employee.
     * @return a string representation of the employee
     */
    @Override
    public String toString() {
        return "Employee{id=" + id
                + ", organization=" + (organization == null ? null : organization.getId())
                + ", name='" + name + "'"
                + ", email='" + email + "'"
                + ", active=" + active + "}";
    }
}
