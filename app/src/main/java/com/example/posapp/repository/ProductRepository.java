package com.example.posapp.repository;

import com.example.posapp.entity.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for managing {@link Product} entities.
 * <p>
 * This interface extends {@code JpaRepository}, which provides CRUD operations
 * and query methods for {@link Product} entities.
 * </p>
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Called by {@link com.example.posapp.service.ProductService} to interact with the database.</li>
 *   <li>Provides methods for finding products by name or retrieving all products.</li>
 * </ul>
 * </p>
 * 
 * @see com.example.posapp.entity.Product
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    /**
     * Find products by name.
     * @param name the name of the product
     * @return a list of products with the given name
     */
    List<Product> findByName(String name);
}
