package com.example.posapp.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.posapp.entity.Category;

/**
 * Repository interface for managing {@link Category} entities.
 * <p>
 * This interface extends {@code JpaRepository}, which provides CRUD operations
 * and query methods for {@link Category} entities.
 * </p>
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Called by {@link com.example.posapp.service.ProductService} to resolve
 *       category references on products.</li>
 *   <li>Called by {@link com.example.posapp.loader.DataLoader} for idempotent
 *       seed-category lookups.</li>
 * </ul>
 * </p>
 *
 * @see com.example.posapp.entity.Category
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Find a category by its unique name.
     * @param name the name of the category
     * @return an Optional containing the category if found, or empty if not
     */
    Optional<Category> findByName(String name);
}
