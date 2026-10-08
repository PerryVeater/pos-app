package com.example.posapp.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import com.example.posapp.entity.Product;
import com.example.posapp.exception.ProductNotFoundException;
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
     * Validates that the price is present and non-negative and that the SKU
     * is present and not already used by another product before saving.
     *
     * @param product the Product to create
     * @return the saved Product
     * @throws IllegalArgumentException if the product price is missing or negative,
     *         the SKU is missing or too long, or the SKU is already in use
     */
    public Product createProduct(Product product) {
        validatePrice(product.getPrice());
        validateSku(product.getSku());
        if (productRepo.existsBySku(product.getSku())) {
            throw new IllegalArgumentException("SKU already exists: " + product.getSku());
        }
        return productRepo.save(product);
    }

    /**
     * Update an existing {@link Product}.
     * Validates that the price is present and non-negative and that the
     * {@link Product} exists before updating. The SKU is rejected when another
     * product already uses it; keeping the product's own SKU is allowed.
     *
     * @param id the ID of the {@link Product} to update
     * @param updatedProduct the updated {@link Product} data
     * @return the updated {@link Product}
     * @throws IllegalArgumentException if the product price is missing or negative,
     *         the SKU is missing or too long, or the SKU is used by another product
     * @throws ProductNotFoundException if the {@link Product} does not exist
     */
    public Product updateProduct(Long id, Product updatedProduct) {
        validatePrice(updatedProduct.getPrice());
        validateSku(updatedProduct.getSku());
        return productRepo.findById(id)
            .map(existing -> {
                if (productRepo.existsBySkuAndIdNot(updatedProduct.getSku(), id)) {
                    throw new IllegalArgumentException("SKU already exists: " + updatedProduct.getSku());
                }
                existing.setName(updatedProduct.getName());
                existing.setSku(updatedProduct.getSku());
                existing.setPrice(updatedProduct.getPrice());
                existing.setActive(updatedProduct.isActive());
                return productRepo.save(existing);
            })
            .orElseThrow(() -> new ProductNotFoundException(id));
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

    /**
     * Reject missing or negative prices for any write operation.
     * @param price the price to validate
     * @throws IllegalArgumentException if the price is missing or negative
     */
    private static void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Price must be provided");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative");
        }
    }

    /**
     * Reject missing, blank, or over-long SKUs for any write operation.
     * Mirrors the API boundary rules and the VARCHAR(64) column so direct
     * service callers cannot bypass them.
     * @param sku the SKU to validate
     * @throws IllegalArgumentException if the SKU is missing, blank, or longer
     *         than 64 characters
     */
    private static void validateSku(String sku) {
        if (sku == null || sku.isBlank()) {
            throw new IllegalArgumentException("SKU must be provided");
        }
        if (sku.length() > 64) {
            throw new IllegalArgumentException("SKU must be at most 64 characters");
        }
    }
}
