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

import com.example.posapp.dto.StoreRequest;
import com.example.posapp.dto.StoreResponse;
import com.example.posapp.entity.Store;
import com.example.posapp.exception.StoreNotFoundException;
import com.example.posapp.service.StoreService;

import jakarta.validation.Valid;

/**
 * REST controller for store operations.
 * <p>
 * Business rules are enforced by {@link StoreService}. The HTTP contract is
 * decoupled from the JPA model via {@link StoreRequest} and
 * {@link StoreResponse}. Stores are also reachable through the organization
 * sub-resource at {@code /api/v1/organizations/{id}/stores}, which reads
 * the same underlying repository.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;

    /**
     * Constructor for StoreController.
     * @param storeService the service for store operations
     */
    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    /**
     * List all stores.
     * @return every store as an API response
     */
    @GetMapping
    public List<StoreResponse> getStores() {
        return storeService.getAllStores().stream()
                .map(StoreResponse::from)
                .toList();
    }

    /**
     * Get a store by ID.
     * @param id the store ID
     * @return the store
     * @throws StoreNotFoundException if the store does not exist
     */
    @GetMapping("/{id}")
    public StoreResponse getStore(@PathVariable Long id) {
        Store store = storeService.getStoreById(id)
                .orElseThrow(() -> new StoreNotFoundException(id));
        return StoreResponse.from(store);
    }

    /**
     * Create a new store owned by the specified organization.
     * @param request the validated store creation payload
     * @return the created store
     */
    @PostMapping
    public ResponseEntity<StoreResponse> createStore(@Valid @RequestBody StoreRequest request) {
        Store saved = storeService.createStore(request.toEntity(), request.organizationId());
        return ResponseEntity.status(201).body(StoreResponse.from(saved));
    }

    /**
     * Update an existing store, including re-parenting it to a different
     * organization.
     * @param id the store ID
     * @param request the replacement values
     * @return the updated store
     */
    @PutMapping("/{id}")
    public StoreResponse updateStore(@PathVariable Long id, @Valid @RequestBody StoreRequest request) {
        Store updated = storeService.updateStore(id, request.toEntity(), request.organizationId());
        return StoreResponse.from(updated);
    }

    /**
     * Delete a store by ID.
     * @param id the store ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStore(@PathVariable Long id) {
        storeService.deleteStore(id);
        return ResponseEntity.noContent().build();
    }
}
