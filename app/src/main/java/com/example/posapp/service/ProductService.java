package com.example.posapp.service;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.example.posapp.entity.Product;
import com.example.posapp.repository.ProductRepository;

/**
 * Service layer responsible for managing {@link Product} entities in the POS application.
 * <p>
 * This class encapsulates business logic for product management, including validation
 * and interaction with the {@link ProductRepository}. It provides CRUD operations
 * (create, read, update, delete) and enforces rules such as preventing negative prices.
 * </p>
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Called by {@link com.example.posapp.controller.ProductController} to process
 *       client requests.</li>
 *   <li>Ensures data integrity before interacting with the database.</li>
 * </ul>
 * </p>
 * 
 * @see com.example.posapp.entity.Product
 * @see com.example.posapp.repository.ProductRepository
 */
@Service
public class ProductService {

    private final ProductRepository productRepo;
    
    /**
     * Constructor for ProductService.
     * @param productRepo The repository for {@link Product}s.
     */
    public ProductService(ProductRepository productRepo) {
        this.productRepo = productRepo;
    }

   /**
     * Create a new {@link Product}.
     * Validates that the price is non-negative before saving.
     *
     * @param product the Product to create
     * @return the saved Product
     * @throws IllegalArgumentException if the product price is negative
     */
    public Product createProduct(Product product) {
        if (product.getPrice() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
        return productRepo.save(product);
    }

    /**
     * Update an existing {@link Product}.
     * Validates that the {@link Product} exists before updating.
     *
     * @param id the ID of the {@link Product} to update
     * @param updatedProduct the updated {@link Product} data
     * @return the updated {@link Product}
     * @throws IllegalArgumentException if the {@link Product} does not exist
     */
    public Product updateProduct(Long id, Product updatedProduct) {
        return productRepo.findById(id)
            .map(existing -> {
                existing.setName(updatedProduct.getName());
                existing.setPrice(updatedProduct.getPrice());
                return productRepo.save(existing);
            })
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));
    }
    
    /**
     * Delete a {@link Product} by ID.
     * Does not throw an exception if the {@link Product} does not exist.
     *
     * @param id the ID of the {@link Product} to delete
     */
    public void deleteProduct(Long id) {
        productRepo.deleteById(id);
    }    

    /**
     * Retrieve all {@link Product}s from the repository.
     *
     * @return a list of all {@link Product}s
     */
    public List<Product> getAllProducts() {
        return productRepo.findAll();
    }

    /**
     * Retrieve a {@link Product} by its ID.
     * Returns Optional.empty() if the {@link Product} does not exist.
     *
     * @param id the ID of the {@link Product} to retrieve
     * @return an Optional containing the {@link Product} if found, or empty if not
     * @throws IllegalArgumentException if the ID is null
     */
    public Optional<Product> getProductById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }
        return productRepo.findById(id);
    }
}
