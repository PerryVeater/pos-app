package com.example.posapp.entity;

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
 *   <li>{@code price} - Price of the product.</li>
 * </ul>
 * </p>
 */
@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private double price;

    /**
     * Default constructor for Product.
     */
    public Product() {}

    /**
     * Constructor for Product.
     * @param name the name of the product
     * @param price the price of the product
     */
    public Product(String name, double price) {
        this.name = name;
        this.price = price;
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
     * Get the price of the product.
     * @return the price of the product
     */
    public double getPrice() { return price; }

    /**
     * Set the price of the product.
     * @param price the price of the product
     */
    public void setPrice(double price) { this.price = price; }
}
