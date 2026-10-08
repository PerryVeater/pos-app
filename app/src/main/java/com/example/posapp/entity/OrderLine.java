package com.example.posapp.entity;

import java.math.BigDecimal;

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
 * Entity class representing a single line item within an {@link Order}.
 * <p>
 * Each line captures the menu item being ordered, the quantity, and the
 * unit price at the time the order was placed. The unit price is a snapshot:
 * later changes to the menu item's price do not affect existing order lines.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the line.</li>
 *   <li>{@code order} - The order this line belongs to.</li>
 *   <li>{@code product} - The menu item being ordered.</li>
 *   <li>{@code quantity} - The number of units ordered.</li>
 *   <li>{@code unitPrice} - The price per unit at order-creation time.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "order_line")
public class OrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The order this line belongs to. The join column and constraint name
     * match the {@code fk_order_line_order} foreign key created by the
     * Flyway V4 migration.
     */
    @ManyToOne
    @JoinColumn(name = "order_id", foreignKey = @ForeignKey(name = "fk_order_line_order"))
    private Order order;

    /**
     * The menu item being ordered. The join column and constraint name match
     * the {@code fk_order_line_product} foreign key created by the Flyway V4
     * migration.
     */
    @ManyToOne
    @JoinColumn(name = "product_id", foreignKey = @ForeignKey(name = "fk_order_line_product"))
    private MenuItem product;

    /**
     * Number of units ordered. Must be positive.
     */
    @Column(nullable = false)
    private int quantity;

    /**
     * Price of a single unit, captured from the menu item's current price at
     * order-creation time. Stored as {@code NUMERIC(10,2)} to match the
     * menu item price column.
     */
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    /**
     * Default constructor for OrderLine.
     */
    public OrderLine() {}

    /**
     * Constructor for OrderLine.
     * @param product the menu item being ordered
     * @param quantity the number of units ordered
     * @param unitPrice the price per unit at order-creation time
     */
    public OrderLine(MenuItem product, int quantity, BigDecimal unitPrice) {
        this.product = product;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    /**
     * Get the ID of the line.
     * @return the ID of the line
     */
    public Long getId() { return id; }

    /**
     * Get the order this line belongs to.
     * @return the order this line belongs to
     */
    public Order getOrder() { return order; }

    /**
     * Set the order this line belongs to.
     * @param order the order this line belongs to
     */
    public void setOrder(Order order) { this.order = order; }

    /**
     * Get the menu item being ordered.
     * @return the menu item being ordered
     */
    public MenuItem getMenuItem() { return product; }

    /**
     * Set the menu item being ordered.
     * @param product the menu item being ordered
     */
    public void setMenuItem(MenuItem product) { this.product = product; }

    /**
     * Get the quantity ordered.
     * @return the quantity ordered
     */
    public int getQuantity() { return quantity; }

    /**
     * Set the quantity ordered.
     * @param quantity the quantity ordered
     */
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /**
     * Get the unit price at order-creation time.
     * @return the unit price at order-creation time
     */
    public BigDecimal getUnitPrice() { return unitPrice; }

    /**
     * Set the unit price at order-creation time.
     * @param unitPrice the unit price at order-creation time
     */
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    /**
     * Return a string representation of the line.
     * @return a string representation of the line
     */
    @Override
    public String toString() {
        return "OrderLine{id=" + id
                + ", productId=" + (product == null ? "null" : product.getId())
                + ", quantity=" + quantity
                + ", unitPrice=" + unitPrice + "}";
    }
}
