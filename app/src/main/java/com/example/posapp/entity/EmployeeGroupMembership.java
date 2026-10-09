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
 * Join entity recording that an {@link Employee} belongs to an
 * {@link EmployeeGroup}.
 * <p>
 * This is the owning side of the many-to-many membership between
 * {@link Employee} and {@link EmployeeGroup}. An explicit entity is used
 * (rather than a plain {@code @ManyToMany}) to follow the project's
 * assignment-layer convention and to allow the row to denormalize
 * {@code organizationId}, which backs the composite foreign keys
 * {@code fk_egm_employee} and {@code fk_egm_employee_group} defined by the
 * Flyway V14 migration. Those composite FKs pin both the employee and the
 * group to the row's organization, so a cross-organization membership is
 * rejected at the database level in addition to the service check.
 * </p>
 * <p>
 * The (employee, group) pair is unique via
 * {@code uk_employee_group_membership}, so a member cannot be attached to
 * the same group twice. Removing the membership leaves both aggregates
 * intact.
 * </p>
 */
@Entity
@Table(name = "employee_group_membership",
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_group_membership",
                columnNames = {"employee_id", "employee_group_id"}))
public class EmployeeGroupMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The employee being attached to the group. The join column and
     * constraint name match the composite {@code fk_egm_employee} foreign
     * key created by the Flyway V14 migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_egm_employee"))
    private Employee employee;

    /**
     * The employee group the employee is being attached to. The join
     * column and constraint name match the composite
     * {@code fk_egm_employee_group} foreign key created by the Flyway V14
     * migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_egm_employee_group"))
    private EmployeeGroup employeeGroup;

    /**
     * Denormalized owning organization ID, populated from the employee on
     * construction. It is the shared column used by both composite foreign
     * keys on this row, which together guarantee the employee and the
     * group belong to the same organization.
     */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /**
     * Default constructor for EmployeeGroupMembership.
     */
    public EmployeeGroupMembership() {}

    /**
     * Constructor for EmployeeGroupMembership.
     * @param employee the employee being attached
     * @param employeeGroup the group the employee is being attached to
     * @throws IllegalArgumentException if either aggregate lacks an
     *         organization, or the two aggregates belong to different
     *         organizations
     */
    public EmployeeGroupMembership(Employee employee, EmployeeGroup employeeGroup) {
        if (employee == null || employee.getOrganization() == null
                || employee.getOrganization().getId() == null) {
            throw new IllegalArgumentException("Employee must have a persisted organization");
        }
        if (employeeGroup == null || employeeGroup.getOrganization() == null
                || employeeGroup.getOrganization().getId() == null) {
            throw new IllegalArgumentException("Employee group must have a persisted organization");
        }
        if (!employee.getOrganization().getId().equals(employeeGroup.getOrganization().getId())) {
            throw new IllegalArgumentException(
                    "Employee and employee group must belong to the same organization");
        }
        this.employee = employee;
        this.employeeGroup = employeeGroup;
        this.organizationId = employee.getOrganization().getId();
    }

    /**
     * Get the ID of the membership.
     * @return the ID of the membership
     */
    public Long getId() { return id; }

    /**
     * Get the employee side of the membership.
     * @return the employee
     */
    public Employee getEmployee() { return employee; }

    /**
     * Set the employee side of the membership.
     * @param employee the employee
     */
    public void setEmployee(Employee employee) { this.employee = employee; }

    /**
     * Get the employee group side of the membership.
     * @return the employee group
     */
    public EmployeeGroup getEmployeeGroup() { return employeeGroup; }

    /**
     * Set the employee group side of the membership.
     * @param employeeGroup the employee group
     */
    public void setEmployeeGroup(EmployeeGroup employeeGroup) { this.employeeGroup = employeeGroup; }

    /**
     * Get the denormalized owning organization ID.
     * @return the organization ID shared by the employee and the group
     */
    public Long getOrganizationId() { return organizationId; }

    /**
     * Set the denormalized owning organization ID.
     * @param organizationId the organization ID
     */
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    /**
     * Return a string representation of the membership.
     * @return a string representation of the membership
     */
    @Override
    public String toString() {
        return "EmployeeGroupMembership{id=" + id
                + ", employeeId=" + (employee == null ? null : employee.getId())
                + ", employeeGroupId=" + (employeeGroup == null ? null : employeeGroup.getId())
                + ", organizationId=" + organizationId + "}";
    }
}
