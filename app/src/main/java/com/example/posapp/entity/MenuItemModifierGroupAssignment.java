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
 * Join entity representing a {@link ModifierGroup} being attached to a
 * {@link MenuItem} at a specific {@code displayOrder}.
 * <p>
 * This is the owning side of the many-to-many relationship between
 * {@link MenuItem} and {@link ModifierGroup}. An explicit entity is required
 * because the association carries a {@code displayOrder} attribute; a plain
 * {@code @ManyToMany} cannot model that.
 * </p>
 * <p>
 * The same {@link ModifierGroup} can appear on multiple {@link MenuItem}s
 * (reusability), but a given (item, group) pair is unique — enforced by
 * the {@code uk_menu_item_modifier_group} constraint. ModifierGroup's own
 * {@code minSelections} / {@code maxSelections} remain intrinsic defaults;
 * per-item overrides are intentionally not modelled here yet.
 * </p>
 */
@Entity
@Table(name = "menu_item_modifier_group_assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_menu_item_modifier_group",
                columnNames = {"menu_item_id", "modifier_group_id"}))
public class MenuItemModifierGroupAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The menu item this assignment belongs to. The join column targets the
     * legacy {@code product} table because {@link MenuItem} preserves that
     * physical name; the constraint name matches the
     * {@code fk_mimg_assignment_item} foreign key created by the Flyway
     * V10 migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mimg_assignment_item"))
    private MenuItem menuItem;

    /**
     * The modifier group being assigned. The join column and constraint
     * names match the {@code fk_mimg_assignment_group} foreign key created
     * by the Flyway V10 migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "modifier_group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mimg_assignment_group"))
    private ModifierGroup modifierGroup;

    /**
     * Position of this modifier group within the menu item. Lower numbers
     * appear first. Required and non-null.
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Default constructor for MenuItemModifierGroupAssignment.
     */
    public MenuItemModifierGroupAssignment() {}

    /**
     * Constructor for MenuItemModifierGroupAssignment.
     * @param menuItem the menu item this assignment belongs to
     * @param modifierGroup the modifier group being assigned
     * @param displayOrder the position of the modifier group within the item
     */
    public MenuItemModifierGroupAssignment(MenuItem menuItem, ModifierGroup modifierGroup, int displayOrder) {
        this.menuItem = menuItem;
        this.modifierGroup = modifierGroup;
        this.displayOrder = displayOrder;
    }

    /**
     * Get the ID of the assignment.
     * @return the ID of the assignment
     */
    public Long getId() { return id; }

    /**
     * Get the menu item.
     * @return the menu item
     */
    public MenuItem getMenuItem() { return menuItem; }

    /**
     * Set the menu item.
     * @param menuItem the menu item
     */
    public void setMenuItem(MenuItem menuItem) { this.menuItem = menuItem; }

    /**
     * Get the assigned modifier group.
     * @return the modifier group
     */
    public ModifierGroup getModifierGroup() { return modifierGroup; }

    /**
     * Set the assigned modifier group.
     * @param modifierGroup the modifier group
     */
    public void setModifierGroup(ModifierGroup modifierGroup) { this.modifierGroup = modifierGroup; }

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
        return "MenuItemModifierGroupAssignment{id=" + id
                + ", menuItemId=" + (menuItem == null ? "null" : menuItem.getId())
                + ", modifierGroupId=" + (modifierGroup == null ? "null" : modifierGroup.getId())
                + ", displayOrder=" + displayOrder + "}";
    }
}
