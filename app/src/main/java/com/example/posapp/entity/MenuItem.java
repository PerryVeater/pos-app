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
 * Entity class representing a menu item in the Point-of-Sale (POS) system.
 * <p>
 * This class is annotated with {@code @Entity} to indicate that it's a JPA entity,
 * and mapped to the "product" table in the database (the table name is preserved
 * for backward compatibility).
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the menu item.</li>
 *   <li>{@code name} - Name of the menu item.</li>
 *   <li>{@code sku} - Stable, caller-supplied identifier used by POS systems.</li>
 *   <li>{@code price} - Price of the menu item.</li>
 *   <li>{@code active} - Whether the menu item is currently available for sale.</li>
 *   <li>{@code category} - Optional category the menu item belongs to.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "product")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /**
     * Stable menu item identifier used by POS systems. Always supplied by the
     * caller (never auto-generated) and unique across all menu items.
     */
    @Column(nullable = false, unique = true, length = 64)
    private String sku;

    /**
     * Monetary amount stored as an exact numeric(10,2) column: precision 10
     * allows prices up to 99,999,999.99 and scale 2 pins the value to cents.
     * Defaults to zero so a request that omits the price behaves as before.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    /**
     * Lifecycle state: whether the menu item is currently available for sale.
     * Inactive menu items are kept rather than deleted, and new menu items
     * default to being sellable.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Category this menu item belongs to, or {@code null} for an uncategorized
     * menu item. Loaded eagerly (the {@code @ManyToOne} default) so the category
     * is available when mapping API responses. The join column and constraint
     * names match the {@code fk_product_category} foreign key created by the
     * Flyway V3 migration.
     */
    @ManyToOne
    @JoinColumn(name = "category_id", foreignKey = @ForeignKey(name = "fk_product_category"))
    private Category category;

    /**
     * Default constructor for MenuItem.
     */
    public MenuItem() {}

    /**
     * Constructor for MenuItem.
     * @param name the name of the menu item
     * @param sku the caller-supplied unique SKU of the menu item
     * @param price the price of the menu item
     * @param active whether the menu item is available for sale
     */
    public MenuItem(String name, String sku, BigDecimal price, boolean active) {
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.active = active;
    }

    /**
     * Get the ID of the menu item.
     * @return the ID of the menu item
     */
    public Long getId() { return id; }

    /**
     * Get the name of the menu item.
     * @return the name of the menu item
     */
    public String getName() { return name; }

    /**
     * Set the name of the menu item.
     * @param name the name of the menu item
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get the SKU of the menu item.
     * @return the SKU of the menu item
     */
    public String getSku() { return sku; }

    /**
     * Set the SKU of the menu item.
     * @param sku the SKU of the menu item
     */
    public void setSku(String sku) { this.sku = sku; }

    /**
     * Get the price of the menu item.
     * @return the price of the menu item
     */
    public BigDecimal getPrice() { return price; }

    /**
     * Set the price of the menu item.
     * @param price the price of the menu item
     */
    public void setPrice(BigDecimal price) { this.price = price; }

    /**
     * Get whether the menu item is available for sale.
     * @return {@code true} if the menu item is currently available for sale
     */
    public boolean isActive() { return active; }

    /**
     * Set whether the menu item is available for sale.
     * @param active whether the menu item is available for sale
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Get the category of the menu item.
     * @return the category of the menu item, or {@code null} if uncategorized
     */
    public Category getCategory() { return category; }

    /**
     * Set the category of the menu item.
     * @param category the category of the menu item, or {@code null} for none
     */
    public void setCategory(Category category) { this.category = category; }

    /**
     * Return a string representation of the menu item.
     * @return a string representation of the menu item
     */
    @Override
    public String toString() {
        return "MenuItem{id=" + id + ", name='" + name + "', sku='" + sku
                + "', price=" + price + ", active=" + active
                + ", category=" + (category == null ? "none" : category.getName()) + "}";
    }
}
