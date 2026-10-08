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

import com.example.posapp.dto.OrganizationRequest;
import com.example.posapp.dto.OrganizationResponse;
import com.example.posapp.dto.StoreResponse;
import com.example.posapp.entity.Organization;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.service.OrganizationService;

import jakarta.validation.Valid;

/**
 * REST controller for organization operations, including the sub-resource
 * for listing the stores owned by an organization.
 * <p>
 * Business rules are enforced by {@link OrganizationService}. The HTTP
 * contract is decoupled from the JPA model via {@link OrganizationRequest}
 * and {@link OrganizationResponse}. Organization payloads do not embed
 * their store list; callers read {@code /api/v1/organizations/{id}/stores}
 * to fetch stores explicitly.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    /**
     * Constructor for OrganizationController.
     * @param organizationService the service for organization operations
     */
    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    /**
     * List all organizations.
     * @return every organization as an API response
     */
    @GetMapping
    public List<OrganizationResponse> getOrganizations() {
        return organizationService.getAllOrganizations().stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    /**
     * Get an organization by ID. Stores are exposed through the dedicated
     * {@code /{id}/stores} sub-resource rather than embedded here.
     * @param id the organization ID
     * @return the organization
     * @throws OrganizationNotFoundException if the organization does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> getOrganization(@PathVariable Long id) {
        Organization organization = organizationService.getOrganizationById(id)
                .orElseThrow(() -> new OrganizationNotFoundException(id));
        return ResponseEntity.ok(OrganizationResponse.from(organization));
    }

    /**
     * Create a new organization.
     * @param request the validated organization creation payload
     * @return the created organization
     */
    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody OrganizationRequest request) {
        Organization saved = organizationService.createOrganization(request.toEntity());
        return ResponseEntity.status(201).body(OrganizationResponse.from(saved));
    }

    /**
     * Update an existing organization's name and active flag.
     * @param id the organization ID
     * @param request the replacement values
     * @return the updated organization
     */
    @PutMapping("/{id}")
    public OrganizationResponse updateOrganization(
            @PathVariable Long id,
            @Valid @RequestBody OrganizationRequest request) {
        Organization updated = organizationService.updateOrganization(id, request.toEntity());
        return OrganizationResponse.from(updated);
    }

    /**
     * Delete an organization by ID. Refuses to delete an organization that
     * still owns any store.
     * @param id the organization ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable Long id) {
        organizationService.deleteOrganization(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * List the stores owned by an organization.
     * @param id the organization ID
     * @return the stores owned by the organization
     */
    @GetMapping("/{id}/stores")
    public List<StoreResponse> listStores(@PathVariable Long id) {
        return organizationService.listStores(id).stream()
                .map(StoreResponse::from)
                .toList();
    }
}
