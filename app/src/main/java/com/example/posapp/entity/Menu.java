package com.example.posapp.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;

/**
 * Entity class representing a menu in the Point-of-Sale (POS) system.
 * <p>
 * A menu is a top-level container (e.g. "Lunch", "Dinner") that groups a
 * set of {@link MenuGroup}s via {@link MenuGroupAssignment}. A single
 * {@link MenuGroup} may be reused across multiple menus.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the menu.</li>
 *   <li>{@code name} - Required, unique menu name.</li>
 *   <li>{@code active} - Whether the menu is currently in service.</li>
 *   <li>{@code assignments} - Menu groups attached to this menu, with
 *       per-relationship display order.</li>
 * </ul>
 * </p>
 */
@Entity
public class Menu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Menu name. Required and unique, matching the {@code uk_menu_name}
     * database constraint.
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Lifecycle state: whether the menu is currently in service. Inactive
     * menus are kept rather than deleted, and new menus default to active.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Menu groups assigned to this menu, in insertion order. The owning side
     * of the association is the {@link MenuGroupAssignment}; cascading removal
     * here means that deleting a menu also removes its assignments (but not
     * the underlying menu groups, which remain reusable).
     */
    @OneToMany(mappedBy = "menu", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MenuGroupAssignment> assignments = new ArrayList<>();

    /**
     * Default constructor for Menu.
     */
    public Menu() {}

    /**
     * Constructor for Menu.
     * @param name the name of the menu
     */
    public Menu(String name) {
        this.name = name;
    }

    /**
     * Get the ID of the menu.
     * @return the ID of the menu
     */
    public Long getId() { return id; }

    /**
     * Get the name of the menu.
     * @return the name of the menu
     */
    public String getName() { return name; }

    /**
     * Set the name of the menu.
     * @param name the name of the menu
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get whether the menu is in service.
     * @return {@code true} if the menu is currently active
     */
    public boolean isActive() { return active; }

    /**
     * Set whether the menu is in service.
     * @param active whether the menu is currently active
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Get the assignments (menu group attachments) belonging to this menu.
     * @return the list of assignments
     */
    public List<MenuGroupAssignment> getAssignments() { return assignments; }

    /**
     * Return a string representation of the menu.
     * @return a string representation of the menu
     */
    @Override
    public String toString() {
        return "Menu{id=" + id + ", name='" + name + "', active=" + active
                + ", assignments=" + assignments.size() + "}";
    }
}
