package com.example.posapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity class representing an organization in the Point-of-Sale (POS) system.
 * <p>
 * An organization is the top-level tenant container (a chain or franchise
 * group) that owns one or more {@link Store}s. Each {@link Store} belongs to
 * exactly one organization. Organizations are independent of the menu,
 * modifier, order, and payment domains; this entity carries only the
 * identity fields required by the tenancy foundation.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the organization.</li>
 *   <li>{@code name} - Required, unique organization name.</li>
 *   <li>{@code active} - Whether the organization is currently in service.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "organization")
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Organization name. Required and unique, matching the
     * {@code uk_organization_name} database constraint.
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Lifecycle state: whether the organization is currently in service.
     * Inactive organizations are kept rather than deleted, and new
     * organizations default to active.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Default constructor for Organization.
     */
    public Organization() {}

    /**
     * Constructor for Organization.
     * @param name the name of the organization
     */
    public Organization(String name) {
        this.name = name;
    }

    /**
     * Get the ID of the organization.
     * @return the ID of the organization
     */
    public Long getId() { return id; }

    /**
     * Get the name of the organization.
     * @return the name of the organization
     */
    public String getName() { return name; }

    /**
     * Set the name of the organization.
     * @param name the name of the organization
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get whether the organization is in service.
     * @return {@code true} if the organization is currently active
     */
    public boolean isActive() { return active; }

    /**
     * Set whether the organization is in service.
     * @param active whether the organization is currently active
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Return a string representation of the organization.
     * @return a string representation of the organization
     */
    @Override
    public String toString() {
        return "Organization{id=" + id + ", name='" + name + "', active=" + active + "}";
    }
}
