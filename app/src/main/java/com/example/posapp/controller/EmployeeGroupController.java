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

import com.example.posapp.dto.EmployeeGroupRequest;
import com.example.posapp.dto.EmployeeGroupResponse;
import com.example.posapp.dto.EmployeeGroupUpdateRequest;
import com.example.posapp.entity.EmployeeGroup;
import com.example.posapp.exception.EmployeeGroupNotFoundException;
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
     * List every employee group.
     * @return all employee groups as API responses
     */
    @GetMapping
    public List<EmployeeGroupResponse> getEmployeeGroups() {
        return employeeGroupService.getAllEmployeeGroups().stream()
                .map(EmployeeGroupResponse::from)
                .toList();
    }

    /**
     * Get an employee group by ID. Child groups are exposed through the
     * dedicated {@code /{id}/children} sub-resource rather than embedded
     * here.
     * @param id the employee group ID
     * @return the employee group
     * @throws EmployeeGroupNotFoundException if no group exists with the ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeGroupResponse> getEmployeeGroup(@PathVariable Long id) {
        EmployeeGroup group = employeeGroupService.getEmployeeGroupById(id)
                .orElseThrow(() -> new EmployeeGroupNotFoundException(id));
        return ResponseEntity.ok(EmployeeGroupResponse.from(group));
    }

    /**
     * Create a new employee group owned by the specified organization,
     * optionally below the specified parent group.
     * @param request the validated employee group creation payload
     * @return the created employee group
     */
    @PostMapping
    public ResponseEntity<EmployeeGroupResponse> createEmployeeGroup(
            @Valid @RequestBody EmployeeGroupRequest request) {
        EmployeeGroup saved = employeeGroupService.createEmployeeGroup(
                request.toEntity(), request.organizationId(), request.parentId());
        return ResponseEntity.status(201).body(EmployeeGroupResponse.from(saved));
    }

    /**
     * Update an existing employee group's name, active flag, and parent.
     * The owning organization is fixed at creation.
     * @param id the employee group ID
     * @param request the replacement values
     * @return the updated employee group
     */
    @PutMapping("/{id}")
    public EmployeeGroupResponse updateEmployeeGroup(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeGroupUpdateRequest request) {
        return EmployeeGroupResponse.from(
                employeeGroupService.updateEmployeeGroup(id, request.toEntity(), request.parentId()));
    }

    /**
     * Delete an employee group by ID. Groups that still have child groups
     * are rejected with 400.
     * @param id the employee group ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployeeGroup(@PathVariable Long id) {
        employeeGroupService.deleteEmployeeGroup(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * List the direct child groups of an employee group.
     * @param id the parent group ID
     * @return the child groups of the parent
     */
    @GetMapping("/{id}/children")
    public List<EmployeeGroupResponse> listChildGroups(@PathVariable Long id) {
        return employeeGroupService.listChildGroups(id).stream()
                .map(EmployeeGroupResponse::from)
                .toList();
    }
}
