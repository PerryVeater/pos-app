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

import com.example.posapp.dto.ProductRequest;
import com.example.posapp.dto.ProductResponse;
import com.example.posapp.service.ProductService;

import jakarta.validation.Valid;

/**
 * Controller class for managing products over HTTP.
 * <p>
 * This class is annotated with {@code @RestController} to indicate that it's a REST controller,
 * and mapped to the "/products" endpoint.
 * </p>
 * <p>
 * The HTTP API is decoupled from the persistence model: requests are received
 * as {@link ProductRequest} and responses are returned as {@link ProductResponse},
 * so the {@code Product} entity is never exposed directly. Typical usage:
 * <ul>
 *   <li>Delegates business logic to {@link com.example.posapp.service.ProductService}.</li>
 *   <li>Provides endpoints for CRUD operations on products.</li>
 * </ul>
 * </p>
 *
 * @see com.example.posapp.dto.ProductRequest
 * @see com.example.posapp.dto.ProductResponse
 * @see com.example.posapp.service.ProductService
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    /**
     * Constructor for ProductController.
     * @param productService the service for products.
     */
    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Get all products.
     * @return a list of all products as API responses
     */
    @GetMapping
    public List<ProductResponse> getProducts() {
        return productService.getAllProducts().stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * Get a product by ID.
     * @param id the ID of the product
     * @return the product with the given ID, or 404 if it does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return productService.getProductById(id)
                .map(ProductResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Add a new product.
     * @param request the validated product to add
     * @return the added product as an API response
     */
    @PostMapping
    public ProductResponse addProduct(@Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.createProduct(request.toEntity()));
    }

    /**
     * Update a product.
     * @param id the ID of the product
     * @param request the validated product update
     * @return the updated product as an API response
     */
    @PutMapping("/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ProductResponse.from(productService.updateProduct(id, request.toEntity()));
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
