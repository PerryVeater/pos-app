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
 * Join entity representing a {@link MenuItem} being attached to a
 * {@link MenuGroup} at a specific {@code displayOrder}.
 * <p>
 * This is the owning side of the many-to-many relationship between
 * {@link MenuGroup} and {@link MenuItem}. An explicit entity is required
 * because the association carries a {@code displayOrder} attribute; a
 * plain {@code @ManyToMany} cannot model that.
 * </p>
 * <p>
 * The same {@link MenuItem} can appear in multiple {@link MenuGroup}s
 * (reusability), but a given (group, item) pair is unique — enforced by
 * the {@code uk_menu_item_assignment} constraint.
 * </p>
 */
@Entity
@Table(name = "menu_item_assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_menu_item_assignment",
                columnNames = {"menu_group_id", "menu_item_id"}))
public class MenuItemAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The menu group this assignment belongs to. The join column and
     * constraint names match the {@code fk_menu_item_assignment_group}
     * foreign key created by the Flyway V8 migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "menu_group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_menu_item_assignment_group"))
    private MenuGroup menuGroup;

    /**
     * The menu item being assigned. The join column and constraint names
     * match the {@code fk_menu_item_assignment_item} foreign key created
     * by the Flyway V8 migration. The physical target table is
     * {@code product}, matching {@link MenuItem}'s preserved table name.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_menu_item_assignment_item"))
    private MenuItem menuItem;

    /**
     * Position of this item within the menu group. Lower numbers appear
     * first. Required and non-null.
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Default constructor for MenuItemAssignment.
     */
    public MenuItemAssignment() {}

    /**
     * Constructor for MenuItemAssignment.
     * @param menuGroup the menu group this assignment belongs to
     * @param menuItem the menu item being assigned
     * @param displayOrder the position of the item within the group
     */
    public MenuItemAssignment(MenuGroup menuGroup, MenuItem menuItem, int displayOrder) {
        this.menuGroup = menuGroup;
        this.menuItem = menuItem;
        this.displayOrder = displayOrder;
    }

    /**
     * Get the ID of the assignment.
     * @return the ID of the assignment
     */
    public Long getId() { return id; }

    /**
     * Get the menu group.
     * @return the menu group
     */
    public MenuGroup getMenuGroup() { return menuGroup; }

    /**
     * Set the menu group.
     * @param menuGroup the menu group
     */
    public void setMenuGroup(MenuGroup menuGroup) { this.menuGroup = menuGroup; }

    /**
     * Get the assigned menu item.
     * @return the menu item
     */
    public MenuItem getMenuItem() { return menuItem; }

    /**
     * Set the assigned menu item.
     * @param menuItem the menu item
     */
    public void setMenuItem(MenuItem menuItem) { this.menuItem = menuItem; }

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
        return "MenuItemAssignment{id=" + id
                + ", menuGroupId=" + (menuGroup == null ? "null" : menuGroup.getId())
                + ", menuItemId=" + (menuItem == null ? "null" : menuItem.getId())
                + ", displayOrder=" + displayOrder + "}";
    }
}
