package com.example.posapp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.entity.Product;
import com.example.posapp.service.ProductService;

/**
 * Controller class for managing {@link Product} entities.
 * <p>
 * This class is annotated with {@code @RestController} to indicate that it's a REST controller,
 * and mapped to the "/products" endpoint.
 * </p>
 * <p>
 * Typical usage:
 * <ul>
 *   <li>Called by {@link com.example.posapp.service.ProductService} to process client requests.</li>
 *   <li>Provides endpoints for CRUD operations on {@link Product} entities.</li>
 * </ul>
 * </p>
 * 
 * @see com.example.posapp.entity.Product
 * @see com.example.posapp.service.ProductService
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    /**
     * Constructor for ProductController.
     * @param productService the service for {@link Product}s.
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Get all products.
     * @return a list of all products
     */
    @GetMapping
    public List<Product> getProducts() {
        return productService.getAllProducts();
    }

    /**
     * Get a product by ID.
     * @param id the ID of the product
     * @return the product with the given ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add a new product.
     * @param product the product to add
     * @return the added product
     */
    @PostMapping
    public Product addProduct(@RequestBody Product product) {
        return productService.createProduct(product);
    }

    /**
     * Update a product.
     * @param id the ID of the product
     * @param product the product to update
     * @return the updated product
     */
    @PutMapping("/{id}")
    public Product updateProduct(@PathVariable Long id, @RequestBody Product product) {
        return productService.updateProduct(id, product);
    }

    /**
     * Delete a product.
     * @param id the ID of the product
     * @return a response entity with no content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}


