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
 * Join entity representing a {@link MenuGroup} being attached to a
 * {@link Menu} at a specific {@code displayOrder}.
 * <p>
 * This is the owning side of the many-to-many relationship between
 * {@link Menu} and {@link MenuGroup}. An explicit entity is required because
 * the association carries a {@code displayOrder} attribute; a plain
 * {@code @ManyToMany} cannot model that.
 * </p>
 * <p>
 * The same {@link MenuGroup} can appear in multiple {@link Menu}s
 * (reusability), but a given (menu, menu group) pair is unique — enforced by
 * the {@code uk_menu_group_assignment} constraint.
 * </p>
 */
@Entity
@Table(name = "menu_group_assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_menu_group_assignment",
                columnNames = {"menu_id", "menu_group_id"}))
public class MenuGroupAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The menu this assignment belongs to. The join column and constraint
     * names match the {@code fk_menu_group_assignment_menu} foreign key
     * created by the Flyway V7 migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "menu_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_menu_group_assignment_menu"))
    private Menu menu;

    /**
     * The menu group being assigned. The join column and constraint names
     * match the {@code fk_menu_group_assignment_group} foreign key created
     * by the Flyway V7 migration.
     */
    @ManyToOne(optional = false)
    @JoinColumn(name = "menu_group_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_menu_group_assignment_group"))
    private MenuGroup menuGroup;

    /**
     * Position of this group within the menu. Lower numbers appear first.
     * Required and non-null.
     */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /**
     * Default constructor for MenuGroupAssignment.
     */
    public MenuGroupAssignment() {}

    /**
     * Constructor for MenuGroupAssignment.
     * @param menu the menu this assignment belongs to
     * @param menuGroup the menu group being assigned
     * @param displayOrder the position of the group within the menu
     */
    public MenuGroupAssignment(Menu menu, MenuGroup menuGroup, int displayOrder) {
        this.menu = menu;
        this.menuGroup = menuGroup;
        this.displayOrder = displayOrder;
    }

    /**
     * Get the ID of the assignment.
     * @return the ID of the assignment
     */
    public Long getId() { return id; }

    /**
     * Get the menu.
     * @return the menu
     */
    public Menu getMenu() { return menu; }

    /**
     * Set the menu.
     * @param menu the menu
     */
    public void setMenu(Menu menu) { this.menu = menu; }

    /**
     * Get the assigned menu group.
     * @return the menu group
     */
    public MenuGroup getMenuGroup() { return menuGroup; }

    /**
     * Set the assigned menu group.
     * @param menuGroup the menu group
     */
    public void setMenuGroup(MenuGroup menuGroup) { this.menuGroup = menuGroup; }

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
        return "MenuGroupAssignment{id=" + id
                + ", menuId=" + (menu == null ? "null" : menu.getId())
                + ", menuGroupId=" + (menuGroup == null ? "null" : menuGroup.getId())
                + ", displayOrder=" + displayOrder + "}";
    }
}
