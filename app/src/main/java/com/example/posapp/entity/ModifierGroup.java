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
import jakarta.persistence.Table;

/**
 * Entity class representing a group of related modifiers in the
 * Point-of-Sale (POS) system.
 * <p>
 * A modifier group bundles a set of selectable {@link Modifier}s and
 * declares the selection policy enforced when the group is attached to a
 * menu item in a future iteration: {@code minSelections} and
 * {@code maxSelections}. The group owns its {@link ModifierGroupAssignment}
 * rows; deleting the group removes its assignments but never the underlying
 * modifiers, which stay reusable across other groups.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code id} - Unique identifier for the group.</li>
 *   <li>{@code name} - Required, unique group name.</li>
 *   <li>{@code minSelections} - Minimum number of modifiers a customer
 *       must pick; non-negative.</li>
 *   <li>{@code maxSelections} - Maximum number of modifiers a customer
 *       may pick; at least {@code minSelections}.</li>
 *   <li>{@code active} - Whether the group is currently in service.</li>
 *   <li>{@code assignments} - Modifiers attached to this group, with
 *       per-relationship display order.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "modifier_group")
public class ModifierGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Modifier group name. Required and unique, matching the
     * {@code uk_modifier_group_name} database constraint.
     */
    @Column(nullable = false, unique = true)
    private String name;

    /**
     * Minimum number of modifiers a customer must select from this group.
     * Non-negative; the DB check {@code chk_modifier_group_min_non_negative}
     * enforces the invariant alongside the service layer.
     */
    @Column(name = "min_selections", nullable = false)
    private int minSelections;

    /**
     * Maximum number of modifiers a customer may select from this group.
     * Must be at least {@code minSelections}; the DB check
     * {@code chk_modifier_group_max_gte_min} enforces the invariant
     * alongside the service layer.
     */
    @Column(name = "max_selections", nullable = false)
    private int maxSelections;

    /**
     * Lifecycle state: whether the group is currently in service. Inactive
     * groups are kept rather than deleted, and new groups default to active.
     */
    @Column(nullable = false)
    private boolean active = true;

    /**
     * Modifiers assigned to this group, in insertion order. The owning side
     * of the association is the {@link ModifierGroupAssignment}; cascading
     * removal here means that deleting a group also removes its assignments
     * (but not the underlying modifiers, which remain reusable).
     */
    @OneToMany(mappedBy = "modifierGroup", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ModifierGroupAssignment> assignments = new ArrayList<>();

    /**
     * Default constructor for ModifierGroup.
     */
    public ModifierGroup() {}

    /**
     * Constructor for ModifierGroup.
     * @param name the group name
     * @param minSelections the minimum number of selections required
     * @param maxSelections the maximum number of selections allowed
     */
    public ModifierGroup(String name, int minSelections, int maxSelections) {
        this.name = name;
        this.minSelections = minSelections;
        this.maxSelections = maxSelections;
    }

    /**
     * Get the ID of the group.
     * @return the ID of the group
     */
    public Long getId() { return id; }

    /**
     * Get the group name.
     * @return the name of the group
     */
    public String getName() { return name; }

    /**
     * Set the group name.
     * @param name the name of the group
     */
    public void setName(String name) { this.name = name; }

    /**
     * Get the minimum number of selections required.
     * @return the minimum selection count
     */
    public int getMinSelections() { return minSelections; }

    /**
     * Set the minimum number of selections required.
     * @param minSelections the minimum selection count
     */
    public void setMinSelections(int minSelections) { this.minSelections = minSelections; }

    /**
     * Get the maximum number of selections allowed.
     * @return the maximum selection count
     */
    public int getMaxSelections() { return maxSelections; }

    /**
     * Set the maximum number of selections allowed.
     * @param maxSelections the maximum selection count
     */
    public void setMaxSelections(int maxSelections) { this.maxSelections = maxSelections; }

    /**
     * Get whether the group is currently in service.
     * @return {@code true} if the group is active
     */
    public boolean isActive() { return active; }

    /**
     * Set whether the group is currently in service.
     * @param active whether the group is active
     */
    public void setActive(boolean active) { this.active = active; }

    /**
     * Get the modifier assignments belonging to this group.
     * @return the list of assignments
     */
    public List<ModifierGroupAssignment> getAssignments() { return assignments; }

    /**
     * Return a string representation of the group.
     * @return a string representation of the group
     */
    @Override
    public String toString() {
        return "ModifierGroup{id=" + id + ", name='" + name
                + "', minSelections=" + minSelections
                + ", maxSelections=" + maxSelections
                + ", active=" + active
                + ", assignments=" + assignments.size() + "}";
    }
}
