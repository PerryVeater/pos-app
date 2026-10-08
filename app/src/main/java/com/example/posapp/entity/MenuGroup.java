package com.example.posapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entity class representing a reusable menu group in the Point-of-Sale (POS)
 * system.
 * <p>
 * A menu group is a collection of related menu items (e.g. "Appetizers",
 * "Desserts"). Menu groups are reusable: the same group may be assigned to
 * multiple {@link Menu}s through {@link MenuGroupAssignment}.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the menu group.</li>
 *   <li>{@code name} - Required, unique menu group name.</li>
 *   <li>{@code active} - Whether the menu group is currently in service.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "menu_group")
public class MenuGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Menu group name. Required and unique, matching the
     * {@code uk_menu_group_name} database constraint.
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Lifecycle state: whether the menu group is currently in service.
     * Inactive groups are kept rather than deleted, and new groups default
     * to active.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Default constructor for MenuGroup.
     */
    public MenuGroup() {}

    /**
     * Constructor for MenuGroup.
     * @param name the name of the menu group
     */
    public MenuGroup(String name) {
        this.name = name;
    }

    /**
     * Get the ID of the menu group.
     * @return the ID of the menu group
     */
    public Long getId() { return id; }

    /**
     * Get the name of the menu group.
     * @return the name of the menu group
     */
    public String getName() { return name; }

    /**
     * Set the name of the menu group.
     * @param name the name of the menu group
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get whether the menu group is in service.
     * @return {@code true} if the menu group is currently active
     */
    public boolean isActive() { return active; }

    /**
     * Set whether the menu group is in service.
     * @param active whether the menu group is currently active
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Return a string representation of the menu group.
     * @return a string representation of the menu group
     */
    @Override
    public String toString() {
        return "MenuGroup{id=" + id + ", name='" + name + "', active=" + active + "}";
    }
}
