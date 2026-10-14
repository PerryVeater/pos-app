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
 * Join entity recording that an {@link Employee} is assigned to a
 * {@link Store}.
 * <p>
 * This is the owning side of the many-to-many assignment between
 * {@link Employee} and {@link Store}. An explicit entity is used
 * (rather than a plain {@code @ManyToMany}) to follow the project's
 * assignment-layer convention and to allow the row to denormalize
 * {@code organizationId}, which backs the composite foreign keys
 * {@code fk_esa_employee} and {@code fk_esa_store} defined by the Flyway
 * V15 migration. Those composite FKs pin both the employee and the store
 * to the row's organization, so a cross-organization assignment is
 * rejected at the database level in addition to the service check.
 * </p>
 * <p>
 * The (employee, store) pair is unique via
 * {@code uk_employee_store_assignment}, so an employee cannot be assigned
 * to the same store twice. Removing the assignment leaves both aggregates
 * intact. Roles, permissions, and scheduling are intentionally not part
 * of this layer.
 * </p>
 */
@Entity
@Table(name = "employee_store_assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_store_assignment",
                columnNames = {"employee_id", "store_id"}))
public class EmployeeStoreAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The employee being assigned to the store. The join column and
     * constraint name match the composite {@code fk_esa_employee} foreign
     * key created by the Flyway V15 migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_esa_employee"))
    private Employee employee;

    /**
     * The store the employee is being assigned to. The join column and
     * constraint name match the composite {@code fk_esa_store} foreign key
     * created by the Flyway V15 migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "store_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_esa_store"))
    private Store store;

    /**
     * Denormalized owning organization ID, populated from the employee on
     * construction. It is the shared column used by both composite foreign
     * keys on this row, which together guarantee the employee and the
     * store belong to the same organization.
     */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /**
     * Default constructor for EmployeeStoreAssignment.
     */
    public EmployeeStoreAssignment() {}

    /**
     * Constructor for EmployeeStoreAssignment.
     * @param employee the employee being assigned
     * @param store the store the employee is being assigned to
     * @throws IllegalArgumentException if either aggregate lacks an
     *         organization, or the two aggregates belong to different
     *         organizations
     */
    public EmployeeStoreAssignment(Employee employee, Store store) {
        if (employee == null || employee.getOrganization() == null
                || employee.getOrganization().getId() == null) {
            throw new IllegalArgumentException("Employee must have a persisted organization");
        }
        if (store == null || store.getOrganization() == null
                || store.getOrganization().getId() == null) {
            throw new IllegalArgumentException("Store must have a persisted organization");
        }
        if (!employee.getOrganization().getId().equals(store.getOrganization().getId())) {
            throw new IllegalArgumentException(
                    "Employee and store must belong to the same organization");
        }
        this.employee = employee;
        this.store = store;
        this.organizationId = employee.getOrganization().getId();
    }

    /**
     * Get the ID of the assignment.
     * @return the ID of the assignment
     */
    public Long getId() { return id; }

    /**
     * Get the employee side of the assignment.
     * @return the employee
     */
    public Employee getEmployee() { return employee; }

    /**
     * Set the employee side of the assignment.
     * @param employee the employee
     */
    public void setEmployee(Employee employee) { this.employee = employee; }

    /**
     * Get the store side of the assignment.
     * @return the store
     */
    public Store getStore() { return store; }

    /**
     * Set the store side of the assignment.
     * @param store the store
     */
    public void setStore(Store store) { this.store = store; }

    /**
     * Get the denormalized owning organization ID.
     * @return the organization ID shared by the employee and the store
     */
    public Long getOrganizationId() { return organizationId; }

    /**
     * Set the denormalized owning organization ID.
     * @param organizationId the organization ID
     */
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }

    /**
     * Return a string representation of the assignment.
     * @return a string representation of the assignment
     */
    @Override
    public String toString() {
        return "EmployeeStoreAssignment{id=" + id
                + ", employeeId=" + (employee == null ? null : employee.getId())
                + ", storeId=" + (store == null ? null : store.getId())
                + ", organizationId=" + organizationId + "}";
    }
}
