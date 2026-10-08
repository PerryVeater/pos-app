package com.example.posapp.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Entity class representing a product in the Point-of-Sale (POS) system.
 * <p>
 * This class is annotated with {@code @Entity} to indicate that it's a JPA entity,
 * and mapped to the "products" table in the database.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the product.</li>
 *   <li>{@code name} - Name of the product.</li>
 *   <li>{@code sku} - Stable, caller-supplied identifier used by POS systems.</li>
 *   <li>{@code price} - Price of the product.</li>
 *   <li>{@code active} - Whether the product is currently available for sale.</li>
 * </ul>
 * </p>
 */
@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /**
     * Stable product identifier used by POS systems. Always supplied by the
     * caller (never auto-generated) and unique across all products.
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
     * Lifecycle state: whether the product is currently available for sale.
     * Inactive products are kept rather than deleted, and new products
     * default to being sellable.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Default constructor for Product.
     */
    public Product() {}

    /**
     * Constructor for Product.
     * @param name the name of the product
     * @param sku the caller-supplied unique SKU of the product
     * @param price the price of the product
     * @param active whether the product is available for sale
     */
    public Product(String name, String sku, BigDecimal price, boolean active) {
        this.name = name;
        this.sku = sku;
        this.price = price;
        this.active = active;
    }

    /**
     * Get the ID of the product.
     * @return the ID of the product
     */
    public Long getId() { return id; }

    /**
     * Get the name of the product.
     * @return the name of the product
     */
    public String getName() { return name; }

    /**
     * Set the name of the product.
     * @param name the name of the product
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get the SKU of the product.
     * @return the SKU of the product
     */
    public String getSku() { return sku; }

    /**
     * Set the SKU of the product.
     * @param sku the SKU of the product
     */
    public void setSku(String sku) { this.sku = sku; }

    /**
     * Get the price of the product.
     * @return the price of the product
     */
    public BigDecimal getPrice() { return price; }

    /**
     * Set the price of the product.
     * @param price the price of the product
     */
    public void setPrice(BigDecimal price) { this.price = price; }

    /**
     * Get whether the product is available for sale.
     * @return {@code true} if the product is currently available for sale
     */
    public boolean isActive() { return active; }

    /**
     * Set whether the product is available for sale.
     * @param active whether the product is available for sale
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Return a string representation of the product.
     * @return a string representation of the product
     */
    @Override
    public String toString() {
        return "Product{id=" + id + ", name='" + name + "', sku='" + sku
                + "', price=" + price + ", active=" + active + "}";
    }
}
