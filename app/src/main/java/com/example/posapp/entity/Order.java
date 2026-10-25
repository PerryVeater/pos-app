package com.example.posapp.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Entity class representing an order in the Point-of-Sale (POS) system.
 * <p>
 * Mapped to the {@code orders} table (the table name avoids the SQL reserved
 * word {@code ORDER}). An order carries a lifecycle {@link OrderStatus}, a
 * {@link DiningOption}, a creation timestamp, and a collection of {@link OrderLine}s
 * that are persisted together via cascade.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the order.</li>
 *   <li>{@code status} - Lifecycle state of the order.</li>
 *   <li>{@code diningOption} - How the order will be consumed.</li>
 *   <li>{@code createdAt} - Timestamp when the order was created.</li>
 *   <li>{@code store} - The owning store, always present and derived from the
 *       authenticated POS session (required).</li>
 *   <li>{@code employee} - The creating employee, always present and derived
 *       from the authenticated POS session (required).</li>
 *   <li>{@code lines} - Line items belonging to this order.</li>
 *   <li>{@code version} - Optimistic-locking counter, bumped on every update.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Optimistic-locking counter (Flyway V23). Hibernate increments it on every
     * update and adds {@code AND version = ?} to the {@code UPDATE}, so two
     * requests that both read the same order cannot both write it: the second
     * fails instead of silently overwriting the first. Mapped as a primitive so
     * a new (unsaved) order starts at zero without a null check anywhere.
     */
    @Version
    @Column(nullable = false)
    private long version;

    /**
     * Lifecycle state of the order. Stored as a {@code VARCHAR(16)} column
     * via {@link EnumType#STRING} so the database values are human-readable.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderStatus status;

    /**
     * Dining option for the order. Stored as a {@code VARCHAR(20)} column
     * via {@link EnumType#STRING} so the database values are human-readable.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "dining_option", nullable = false, length = 20)
    private DiningOption diningOption;

    /**
     * Timestamp when the order was created. Set by the service layer at
     * order-creation time.
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /**
     * The store the order belongs to. Required: the POS order lifecycle is the
     * only way an order is created, and it always stamps the store derived from
     * the authenticated bearer session - the client never supplies it. Loaded
     * eagerly so the ID is available when mapping responses outside a
     * transaction. The join column and constraint name match the
     * {@code fk_orders_store} foreign key (Flyway V18), tightened to NOT NULL by
     * Flyway V19.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "store_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_orders_store"))
    private Store store;

    /**
     * The employee who created the order. Required, derived from the
     * authenticated bearer session and never supplied by the client. The join
     * column and constraint name match the {@code fk_orders_employee} foreign
     * key (Flyway V18), tightened to NOT NULL by Flyway V19.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_orders_employee"))
    private Employee employee;

    /**
     * Line items belonging to this order. Loaded eagerly so the lines are
     * available when mapping responses outside a transaction. Cascade
     * {@code ALL} with orphan removal means saving an order persists its
     * lines, and removing a line from the list deletes it.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderLine> lines = new ArrayList<>();

    /**
     * Default constructor for Order.
     */
    public Order() {}

    /**
     * Constructor for Order.
     * @param status the lifecycle state of the order
     * @param diningOption how the order will be consumed
     * @param createdAt the timestamp when the order was created
     */
    public Order(OrderStatus status, DiningOption diningOption, LocalDateTime createdAt) {
        this.status = status;
        this.diningOption = diningOption;
        this.createdAt = createdAt;
    }

    /**
     * Get the ID of the order.
     * @return the ID of the order
     */
    public Long getId() { return id; }

    /**
     * Get the optimistic-locking version of the order.
     * @return the version, zero for an order that has never been updated
     */
    public long getVersion() { return version; }

    /**
     * Get the status of the order.
     * @return the status of the order
     */
    public OrderStatus getStatus() { return status; }

    /**
     * Set the status of the order.
     * @param status the status of the order
     */
    public void setStatus(OrderStatus status) { this.status = status; }

    /**
     * Get the dining option of the order.
     * @return the dining option of the order
     */
    public DiningOption getDiningOption() { return diningOption; }

    /**
     * Set the dining option of the order.
     * @param diningOption the dining option of the order
     */
    public void setDiningOption(DiningOption diningOption) { this.diningOption = diningOption; }

    /**
     * Get the creation timestamp of the order.
     * @return the creation timestamp of the order
     */
    public LocalDateTime getCreatedAt() { return createdAt; }

    /**
     * Set the creation timestamp of the order.
     * @param createdAt the creation timestamp of the order
     */
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    /**
     * Get the store the order belongs to.
     * @return the owning store, always present for a persisted order
     */
    public Store getStore() { return store; }

    /**
     * Set the store the order belongs to.
     * @param store the store derived from the authenticated session
     */
    public void setStore(Store store) { this.store = store; }

    /**
     * Get the employee who created the order.
     * @return the creating employee, always present for a persisted order
     */
    public Employee getEmployee() { return employee; }

    /**
     * Set the employee who created the order.
     * @param employee the employee derived from the authenticated session
     */
    public void setEmployee(Employee employee) { this.employee = employee; }

    /**
     * Get the line items of the order.
     * @return the line items of the order
     */
    public List<OrderLine> getLines() { return lines; }

    /**
     * Add a line item to this order, setting the back-reference to this order.
     * @param line the line to add
     */
    public void addLine(OrderLine line) {
        line.setOrder(this);
        lines.add(line);
    }

    /**
     * Calculate the total amount for this order by summing
     * {@code quantity × unitPrice} across all line items.
     * <p> This is a computed, transient value — it is not persisted.
     * Returns {@link BigDecimal#ZERO} when the order has no lines.
     * </p>
     *
     * @return the order total as a {@link BigDecimal}
     */
    public BigDecimal getTotal() {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        return lines.stream()
                .map(line -> line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())))
                .reduce(zero, BigDecimal::add);
    }

    /**
     * Return a string representation of the order.
     * @return a string representation of the order
     */
    @Override
    public String toString() {
        return "Order{id=" + id + ", status=" + status
                + ", createdAt=" + createdAt
                + ", store=" + (store == null ? null : store.getId())
                + ", employee=" + (employee == null ? null : employee.getId())
                + ", lines=" + lines.size() + "}";
    }
}
