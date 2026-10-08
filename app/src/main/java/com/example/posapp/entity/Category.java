package com.example.posapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Entity class representing a product category in the Point-of-Sale (POS) system.
 * <p>
 * This class is annotated with {@code @Entity} to indicate that it's a JPA entity,
 * and mapped to the "category" table in the database.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the category.</li>
 *   <li>{@code name} - Name of the category; required and unique.</li>
 * </ul>
 * </p>
 */
@Entity
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Category name. Required and unique: two categories cannot share a name,
     * matching the {@code uk_category_name} database constraint.
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Default constructor for Category.
     */
    public Category() {}

    /**
     * Constructor for Category.
     * @param name the name of the category
     */
    public Category(String name) {
        this.name = name;
    }

    /**
     * Get the ID of the category.
     * @return the ID of the category
     */
    public Long getId() { return id; }

    /**
     * Get the name of the category.
     * @return the name of the category
     */
    public String getName() { return name; }

    /**
     * Set the name of the category.
     * @param name the name of the category
     */
    public void setName(String name) { this.name = name; }

    /**
     * Return a string representation of the category.
     * @return a string representation of the category
     */
    @Override
    public String toString() {
        return "Category{id=" + id + ", name='" + name + "'}";
    }
}
