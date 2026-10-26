package com.example.posapp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.example.posapp.security.BackOfficePrincipal;
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
 * <p>
 * Every handler is confined to the caller's brand. The brand comes from the
 * authenticated principal and is passed to the service, where it is part of
 * every lookup, so another brand's store answers {@code 404} exactly like a
 * store that does not exist.
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
     * List the stores in the caller's brand.
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the stores of that brand as API responses
     */
    @GetMapping
    public List<StoreResponse> getStores(@AuthenticationPrincipal BackOfficePrincipal principal) {
        return storeService.getAllStores(principal.organizationId()).stream()
                .map(StoreResponse::from)
                .toList();
    }

    /**
     * Get a store by ID, within the caller's brand.
     * @param id the store ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the store
     * @throws StoreNotFoundException if the store does not exist in the
     *         caller's brand
     */
    @GetMapping("/{id}")
    public StoreResponse getStore(@PathVariable Long id,
                                  @AuthenticationPrincipal BackOfficePrincipal principal) {
        Store store = storeService.getStoreById(id, principal.organizationId())
                .orElseThrow(() -> new StoreNotFoundException(id));
        return StoreResponse.from(store);
    }

    /**
     * Create a new store. The payload's organization must be the caller's
     * brand.
     * @param request the validated store creation payload
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the created store
     */
    @PostMapping
    public ResponseEntity<StoreResponse> createStore(
            @Valid @RequestBody StoreRequest request,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        Store saved = storeService.createStore(request.toEntity(),
                request.organizationId(), principal.organizationId());
        return ResponseEntity.status(201).body(StoreResponse.from(saved));
    }

    /**
     * Update an existing store within the caller's brand.
     * @param id the store ID
     * @param request the replacement values
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the updated store
     */
    @PutMapping("/{id}")
    public StoreResponse updateStore(@PathVariable Long id,
                                     @Valid @RequestBody StoreRequest request,
                                     @AuthenticationPrincipal BackOfficePrincipal principal) {
        Store updated = storeService.updateStore(id, request.toEntity(),
                request.organizationId(), principal.organizationId());
        return StoreResponse.from(updated);
    }

    /**
     * Delete a store by ID, within the caller's brand. Returns 404 when the
     * store does not exist in that brand and 400 when it still has employee
     * assignments; neither a missing store nor an attached one is deleted.
     * @param id the store ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStore(@PathVariable Long id,
                                            @AuthenticationPrincipal BackOfficePrincipal principal) {
        storeService.deleteStore(id, principal.organizationId());
        return ResponseEntity.noContent().build();
    }
}
