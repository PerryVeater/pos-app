package com.example.posapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Join entity representing a {@link Modifier} being attached to a
 * {@link ModifierGroup} at a specific {@code displayOrder}.
 * <p>
 * This is the owning side of the many-to-many relationship between
 * {@link ModifierGroup} and {@link Modifier}. An explicit entity is required
 * because the association carries a {@code displayOrder} attribute; a plain
 * {@code @ManyToMany} cannot model that.
 * </p>
 * <p>
 * The same {@link Modifier} can appear in multiple {@link ModifierGroup}s
 * (reusability), but a given (group, modifier) pair is unique — enforced
 * by the {@code uk_modifier_group_assignment} constraint.
 * </p>
 */
@Entity
@Table(name = "modifier_group_assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_modifier_group_assignment",
                columnNames = {"modifier_group_id", "modifier_id"}))
public class ModifierGroupAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The modifier group this assignment belongs to. The join column and
     * constraint names match the {@code fk_modifier_group_assignment_group}
     * foreign key created by the Flyway V9 migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "modifier_group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_modifier_group_assignment_group"))
    private ModifierGroup modifierGroup;

    /**
     * The modifier being assigned. The join column and constraint names
     * match the {@code fk_modifier_group_assignment_modifier} foreign key
     * created by the Flyway V9 migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "modifier_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_modifier_group_assignment_modifier"))
    private Modifier modifier;

    /**
     * Position of this modifier within the group. Lower numbers appear
     * first. Required and non-null.
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Default constructor for ModifierGroupAssignment.
     */
    public ModifierGroupAssignment() {}

    /**
     * Constructor for ModifierGroupAssignment.
     * @param modifierGroup the modifier group this assignment belongs to
     * @param modifier the modifier being assigned
     * @param displayOrder the position of the modifier within the group
     */
    public ModifierGroupAssignment(ModifierGroup modifierGroup, Modifier modifier, int displayOrder) {
        this.modifierGroup = modifierGroup;
        this.modifier = modifier;
        this.displayOrder = displayOrder;
    }

    /**
     * Get the ID of the assignment.
     * @return the ID of the assignment
     */
    public Long getId() { return id; }

    /**
     * Get the modifier group.
     * @return the modifier group
     */
    public ModifierGroup getModifierGroup() { return modifierGroup; }

    /**
     * Set the modifier group.
     * @param modifierGroup the modifier group
     */
    public void setModifierGroup(ModifierGroup modifierGroup) { this.modifierGroup = modifierGroup; }

    /**
     * Get the assigned modifier.
     * @return the modifier
     */
    public Modifier getModifier() { return modifier; }

    /**
     * Set the assigned modifier.
     * @param modifier the modifier
     */
    public void setModifier(Modifier modifier) { this.modifier = modifier; }

    /**
     * Get the display order.
     * @return the display order
     */
    public int getDisplayOrder() { return displayOrder; }

    /**
     * Set the display order.
     * @param displayOrder the display order
     */
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

    /**
     * Return a string representation of the assignment.
     * @return a string representation of the assignment
     */
    @Override
    public String toString() {
        return "ModifierGroupAssignment{id=" + id
                + ", modifierGroupId=" + (modifierGroup == null ? "null" : modifierGroup.getId())
                + ", modifierId=" + (modifier == null ? "null" : modifier.getId())
                + ", displayOrder=" + displayOrder + "}";
    }
}
