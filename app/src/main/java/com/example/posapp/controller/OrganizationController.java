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

import com.example.posapp.dto.EmployeeGroupResponse;
import com.example.posapp.dto.OrganizationRequest;
import com.example.posapp.dto.OrganizationResponse;
import com.example.posapp.dto.StoreResponse;
import com.example.posapp.entity.Organization;
import com.example.posapp.exception.OrganizationNotFoundException;
import com.example.posapp.security.BackOfficePrincipal;
import com.example.posapp.service.OrganizationService;

import jakarta.validation.Valid;

/**
 * REST controller for organization operations, including the sub-resources
 * for listing the stores and the root employee groups owned by an
 * organization.
 * <p>
 * Business rules are enforced by {@link OrganizationService}. The HTTP
 * contract is decoupled from the JPA model via {@link OrganizationRequest}
 * and {@link OrganizationResponse}. Organization payloads do not embed
 * their store list; callers read {@code /api/v1/organizations/{id}/stores}
 * to fetch stores explicitly.
 * </p>
 * <p>
 * The caller sees one organization - its own brand. Reading or updating
 * another brand answers {@code 404} like any missing record, while creating
 * and deleting a brand answer {@code 403}: a brand administrator acts
 * inside a brand and cannot add or remove one.
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
     * List organizations. The back office is scoped to one brand, so this
     * returns the caller's organization alone.
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the caller's organization as an API response
     */
    @GetMapping
    public List<OrganizationResponse> getOrganizations(
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        return organizationService.getAllOrganizations(principal.organizationId()).stream()
                .map(OrganizationResponse::from)
                .toList();
    }

    /**
     * Get an organization by ID, within the caller's brand. Stores are
     * exposed through the dedicated {@code /{id}/stores} sub-resource rather
     * than embedded here.
     * @param id the organization ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the organization
     * @throws OrganizationNotFoundException if the organization does not
     *         exist in the caller's brand
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrganizationResponse> getOrganization(
            @PathVariable Long id,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        Organization organization = organizationService
                .getOrganizationById(id, principal.organizationId())
                .orElseThrow(() -> new OrganizationNotFoundException(id));
        return ResponseEntity.ok(OrganizationResponse.from(organization));
    }

    /**
     * Create a new organization. Refused for a brand administrator: a brand
     * is not something a brand can add, so this answers {@code 403} rather
     * than creating a second tenant the caller has no relationship with.
     * @param request the validated organization creation payload
     * @param principal the authenticated caller, whose brand scopes the request
     * @return never returns; the operation is outside every brand
     */
    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @Valid @RequestBody OrganizationRequest request,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        Organization saved = organizationService.createOrganization(request.toEntity(),
                principal.organizationId());
        return ResponseEntity.status(201).body(OrganizationResponse.from(saved));
    }

    /**
     * Update the caller's organization name and active flag.
     * @param id the organization ID
     * @param request the replacement values
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the updated organization
     */
    @PutMapping("/{id}")
    public OrganizationResponse updateOrganization(
            @PathVariable Long id,
            @Valid @RequestBody OrganizationRequest request,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        Organization updated = organizationService.updateOrganization(id, request.toEntity(),
                principal.organizationId());
        return OrganizationResponse.from(updated);
    }

    /**
     * Delete an organization by ID. Refused for a brand administrator: the
     * tenant it is authenticated inside cannot be destroyed from within, so
     * this answers {@code 403}.
     * @param id the organization ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return never returns; the operation is outside every brand
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrganization(
            @PathVariable Long id,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        organizationService.deleteOrganization(id, principal.organizationId());
        return ResponseEntity.noContent().build();
    }

    /**
     * List the stores owned by the caller's brand.
     * @param id the organization ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the stores owned by the organization
     */
    @GetMapping("/{id}/stores")
    public List<StoreResponse> listStores(@PathVariable Long id,
                                          @AuthenticationPrincipal BackOfficePrincipal principal) {
        return organizationService.listStores(id, principal.organizationId()).stream()
                .map(StoreResponse::from)
                .toList();
    }

    /**
     * List the root employee groups owned by the caller's brand. Child
     * groups are exposed through {@code GET /api/v1/employee-groups/{id}/children}.
     * @param id the organization ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the root employee groups owned by the organization
     */
    @GetMapping("/{id}/employee-groups")
    public List<EmployeeGroupResponse> listEmployeeGroups(
            @PathVariable Long id,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        return organizationService.listRootEmployeeGroups(id, principal.organizationId()).stream()
                .map(EmployeeGroupResponse::from)
                .toList();
    }
}
