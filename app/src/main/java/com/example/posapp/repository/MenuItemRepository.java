package com.example.posapp.repository;

import com.example.posapp.entity.MenuItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for managing {@link MenuItem} entities.
 * <p>
 * This interface extends {@code JpaRepository}, which provides CRUD operations
 * and query methods for {@link MenuItem} entities.
 * </p>
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Called by {@link com.example.posapp.service.MenuItemService} to interact with the database.</li>
 *   <li>Provides methods for finding menu items by name or retrieving all menu items.</li>
 * </ul>
 * </p>
 * 
 * @see com.example.posapp.entity.MenuItem
 */
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    
    /**
     * Find menu items by name.
     * @param name the name of the menu item
     * @return a list of menu items with the given name
     */
    List<MenuItem> findByName(String name);

    /**
     * Check whether a menu item with the given name exists.
     * @param name the name of the menu item
     * @return {@code true} if a menu item with the given name exists
     */
    boolean existsByName(String name);

    /**
     * Check whether a menu item with the given SKU exists.
     * @param sku the SKU of the menu item
     * @return {@code true} if a menu item with the given SKU exists
     */
    boolean existsBySku(String sku);

    /**
     * Check whether a menu item other than the one with the given ID uses the SKU.
     * @param sku the SKU to check for conflicts
     * @param id the ID of the menu item being updated, excluded from the check
     * @return {@code true} if another menu item already uses the given SKU
     */
    boolean existsBySkuAndIdNot(String sku, Long id);
}
