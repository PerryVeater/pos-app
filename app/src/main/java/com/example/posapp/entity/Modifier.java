package com.example.posapp.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * Entity class representing a reusable modifier option in the Point-of-Sale
 * (POS) system.
 * <p>
 * A modifier is a selectable add-on (e.g. "Extra cheese", "Whole milk")
 * carrying a price adjustment. Modifiers are intentionally decoupled from
 * any single modifier group: the same {@link Modifier} can appear in many
 * {@link ModifierGroup}s through {@link ModifierGroupAssignment}.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the modifier.</li>
 *   <li>{@code name} - Required modifier name. Not enforced unique because
 *       the same label can legitimately exist in different contexts.</li>
 *   <li>{@code priceAdjustment} - Exact monetary delta applied on top of
 *       the base item's price when the modifier is selected; may be
 *       positive, zero, or negative.</li>
 *   <li>{@code active} - Whether the modifier is currently offered.</li>
 * </ul>
 * </p>
 */
@Entity
public class Modifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Modifier name. Required. Not unique: the same label may exist in
     * different modifier groups (e.g. "Extra cheese" for pizza and for
     * pasta).
     */
    @Column(nullable = false)
    private String name;

    /**
     * Price adjustment in exact monetary terms. {@code NUMERIC(10,2)}
     * matches the project-wide money scale convention so cent-precise
     * arithmetic survives round-trips. Defaults to zero so free modifiers
     * need not supply a value.
     */
    @Column(name = "price_adjustment", precision = 10, scale = 2, nullable = false)
    private BigDecimal priceAdjustment = BigDecimal.ZERO;

    /**
     * Lifecycle state: whether the modifier is currently offered. Inactive
     * modifiers are kept rather than deleted, and new modifiers default
     * to active.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Default constructor for Modifier.
     */
    public Modifier() {}

    /**
     * Constructor for Modifier.
     * @param name the modifier name
     * @param priceAdjustment the price adjustment applied when selected
     */
    public Modifier(String name, BigDecimal priceAdjustment) {
        this.name = name;
        this.priceAdjustment = priceAdjustment;
    }

    /**
     * Get the ID of the modifier.
     * @return the ID of the modifier
     */
    public Long getId() { return id; }

    /**
     * Get the modifier name.
     * @return the name of the modifier
     */
    public String getName() { return name; }

    /**
     * Set the modifier name.
     * @param name the name of the modifier
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get the price adjustment.
     * @return the exact monetary adjustment for this modifier
     */
    public BigDecimal getPriceAdjustment() { return priceAdjustment; }

    /**
     * Set the price adjustment.
     * @param priceAdjustment the exact monetary adjustment
     */
    public void setPriceAdjustment(BigDecimal priceAdjustment) { this.priceAdjustment = priceAdjustment; }

    /**
     * Get whether the modifier is currently offered.
     * @return {@code true} if the modifier is active
     */
    public boolean isActive() { return active; }

    /**
     * Set whether the modifier is currently offered.
     * @param active whether the modifier is active
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Return a string representation of the modifier.
     * @return a string representation of the modifier
     */
    @Override
    public String toString() {
        return "Modifier{id=" + id + ", name='" + name + "', priceAdjustment="
                + priceAdjustment + ", active=" + active + "}";
    }
}
