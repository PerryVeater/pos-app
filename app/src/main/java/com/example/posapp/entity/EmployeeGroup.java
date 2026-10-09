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
 * Entity class representing an employee group in the Point-of-Sale (POS)
 * system.
 * <p>
 * An employee group is a node in a per-organization hierarchy owned by
 * exactly one {@link Organization}. Root groups have no {@code parent};
 * child groups reference another group of the same organization, so a
 * hierarchy never crosses organization boundaries. The database enforces
 * the same-organization rule through the composite
 * {@code fk_employee_group_parent} foreign key on
 * {@code (parent_employee_group_id, organization_id)} referencing
 * {@code (id, organization_id)}, supported by the
 * {@code uk_employee_group_id_organization} unique constraint, and blocks
 * self-parenting through {@code chk_employee_group_not_self_parent}.
 * Indirect cycles are rejected at the service layer because they cannot
 * be expressed as a row-level constraint.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the group.</li>
 *   <li>{@code organization} - Required owning organization; matches the
 *       {@code fk_employee_group_organization} constraint.</li>
 *   <li>{@code parent} - Optional parent group; {@code null} marks a root.</li>
 *   <li>{@code name} - Required group name; not unique, so two groups may
 *       share a name within or across organizations.</li>
 *   <li>{@code active} - Whether the group is currently in service.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "employee_group")
public class EmployeeGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owning organization. Required: an employee group cannot exist
     * without one, so the column is NOT NULL and the join column
     * references {@code organization(id)} through
     * {@code fk_employee_group_organization}.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "organization_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_employee_group_organization"))
    private Organization organization;

    /**
     * Optional parent employee group. {@code null} marks a root group.
     * Parent and child always belong to the same organization: the
     * composite {@code fk_employee_group_parent} foreign key references
     * {@code employee_group(id, organization_id)}, so a cross-organization
     * parent is rejected by the database, while assigning a group as its
     * own parent or introducing an indirect cycle is rejected by the
     * service layer.
     */
    @ManyToOne(optional = true)
    @JoinColumn(name = "parent_employee_group_id",
            foreignKey = @ForeignKey(name = "fk_employee_group_parent"))
    private EmployeeGroup parent;

    /**
     * Employee group name. Required but not unique: two groups may share
     * a name within or across organizations.
     */
    @Column(nullable = false)
    private String name;

    /**
     * Lifecycle state: whether the group is currently in service. Inactive
     * groups are kept rather than deleted, and new groups default to
     * active.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Default constructor for EmployeeGroup.
     */
    public EmployeeGroup() {}

    /**
     * Constructor for EmployeeGroup.
     * @param name the name of the employee group
     */
    public EmployeeGroup(String name) {
        this.name = name;
    }

    /**
     * Get the ID of the employee group.
     * @return the ID of the employee group
     */
    public Long getId() { return id; }

    /**
     * Get the owning organization.
     * @return the organization that owns this group
     */
    public Organization getOrganization() { return organization; }

    /**
     * Set the owning organization.
     * @param organization the organization that owns this group
     */
    public void setOrganization(Organization organization) { this.organization = organization; }

    /**
     * Get the optional parent group.
     * @return the parent group, or {@code null} for a root group
     */
    public EmployeeGroup getParent() { return parent; }

    /**
     * Set the optional parent group.
     * @param parent the parent group, or {@code null} to make a root group
     */
    public void setParent(EmployeeGroup parent) { this.parent = parent; }

    /**
     * Get the name of the employee group.
     * @return the name of the employee group
     */
    public String getName() { return name; }

    /**
     * Set the name of the employee group.
     * @param name the name of the employee group
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get whether the group is in service.
     * @return {@code true} if the group is currently active
     */
    public boolean isActive() { return active; }

    /**
     * Set whether the group is in service.
     * @param active whether the group is currently active
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Return a string representation of the employee group.
     * @return a string representation of the employee group
     */
    @Override
    public String toString() {
        return "EmployeeGroup{id=" + id
                + ", organization=" + (organization == null ? null : organization.getId())
                + ", parent=" + (parent == null ? null : parent.getId())
                + ", name='" + name + "', active=" + active + "}";
    }
}
