package com.example.posapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.EmployeeGroupRequest;
import com.example.posapp.dto.EmployeeGroupResponse;
import com.example.posapp.dto.EmployeeGroupUpdateRequest;
import com.example.posapp.dto.PageRequests;
import com.example.posapp.dto.PageResponse;
import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.exception.EmployeeGroupNotFoundException;
import com.example.posapp.security.BackOfficePrincipal;
import com.example.posapp.service.EmployeeGroupService;

import jakarta.validation.Valid;

/**
 * REST controller for employee group operations.
 * <p>
 * Employee groups form a hierarchy inside a single organization: root
 * groups have no parent, child groups hang below another group of the
 * same organization. Business rules (required name, same-organization
 * parents, cycle prevention, delete guard) are enforced by
 * {@link EmployeeGroupService}. The HTTP contract is decoupled from the
 * JPA model via {@link EmployeeGroupRequest},
 * {@link EmployeeGroupUpdateRequest}, and {@link EmployeeGroupResponse}.
 * Root groups are also reachable through the organization sub-resource at
 * {@code /api/v1/organizations/{id}/employee-groups}.
 * </p>
 * <p>
 * Every handler is confined to the caller's brand, so a group - or a
 * requested parent - belonging to another brand answers {@code 404} exactly
 * like a group that does not exist.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/employee-groups")
public class EmployeeGroupController {

    private final EmployeeGroupService employeeGroupService;

    /**
     * Constructor for EmployeeGroupController.
     * @param employeeGroupService the service for employee group operations
     */
    public EmployeeGroupController(EmployeeGroupService employeeGroupService) {
        this.employeeGroupService = employeeGroupService;
    }

    /**
     * List the employee groups in the caller's brand, one page at a time.
     * @param page the zero-based page to serve, or null for the first
     * @param size the requested page size, or null for the default
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the requested page of that brand's employee groups
     */
    @GetMapping
    public PageResponse<EmployeeGroupResponse> getEmployeeGroups(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        return PageResponse.from(
                employeeGroupService.getAllEmployeeGroups(principal.organizationId(),
                        PageRequests.of(page, size).toPageable()),
                EmployeeGroupResponse::from);
    }

    /**
     * Get an employee group by ID, within the caller's brand. Child groups
     * are exposed through the dedicated {@code /{id}/children} sub-resource
     * rather than embedded here.
     * @param id the employee group ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the employee group
     * @throws EmployeeGroupNotFoundException if no group exists with the ID
     *         in the caller's brand
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeGroupResponse> getEmployeeGroup(
            @PathVariable Long id,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        EmployeeGroup group = employeeGroupService
                .getEmployeeGroupById(id, principal.organizationId())
                .orElseThrow(() -> new EmployeeGroupNotFoundException(id));
        return ResponseEntity.ok(EmployeeGroupResponse.from(group));
    }

    /**
     * Create a new employee group in the caller's brand, optionally below
     * the specified parent group. The payload's organization must be that
     * brand.
     * @param request the validated employee group creation payload
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the created employee group
     */
    @PostMapping
    public ResponseEntity<EmployeeGroupResponse> createEmployeeGroup(
            @Valid @RequestBody EmployeeGroupRequest request,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        EmployeeGroup saved = employeeGroupService.createEmployeeGroup(
                request.toEntity(), request.organizationId(), request.parentId(),
                principal.organizationId());
        return ResponseEntity.status(201).body(EmployeeGroupResponse.from(saved));
    }

    /**
     * Update an existing employee group's name, active flag, and parent,
     * within the caller's brand. The owning organization is fixed at
     * creation.
     * @param id the employee group ID
     * @param request the replacement values
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the updated employee group
     */
    @PutMapping("/{id}")
    public EmployeeGroupResponse updateEmployeeGroup(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeGroupUpdateRequest request,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        return EmployeeGroupResponse.from(employeeGroupService.updateEmployeeGroup(
                id, request.toEntity(), request.parentId(), principal.organizationId()));
    }

    /**
     * Delete an employee group by ID, within the caller's brand. Groups that
     * still have child groups are rejected with 400.
     * @param id the employee group ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployeeGroup(
            @PathVariable Long id,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        employeeGroupService.deleteEmployeeGroup(id, principal.organizationId());
        return ResponseEntity.noContent().build();
    }

    /**
     * List the direct child groups of an employee group, within the
     * caller's brand, one page at a time.
     * @param id the parent group ID
     * @param page the zero-based page to serve, or null for the first
     * @param size the requested page size, or null for the default
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the requested page of child groups
     */
    @GetMapping("/{id}/children")
    public PageResponse<EmployeeGroupResponse> listChildGroups(
            @PathVariable Long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        return PageResponse.from(
                employeeGroupService.listChildGroups(id, principal.organizationId(),
                        PageRequests.of(page, size).toPageable()),
                EmployeeGroupResponse::from);
    }
}
