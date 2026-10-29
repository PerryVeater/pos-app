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

import com.example.posapp.dto.EmployeeRequest;
import com.example.posapp.dto.EmployeeResponse;
import com.example.posapp.dto.PageRequests;
import com.example.posapp.dto.PageResponse;
import com.example.posapp.entity.Employee;
import com.example.posapp.exception.EmployeeNotFoundException;
import com.example.posapp.security.BackOfficePrincipal;
import com.example.posapp.service.EmployeeService;

import jakarta.validation.Valid;

/**
 * REST controller for employee operations.
 * <p>
 * Business rules (required name, existing organization, email uniqueness
 * within the organization) are enforced by {@link EmployeeService}. The
 * HTTP contract is decoupled from the JPA model via
 * {@link EmployeeRequest} and {@link EmployeeResponse}.
 * </p>
 * <p>
 * Every handler is confined to the caller's brand. The brand comes from the
 * authenticated principal - the organization of the employee the token's
 * subject is linked to - and is passed to the service, where it is part of
 * every lookup. A record belonging to another brand therefore answers
 * {@code 404}, which is the same answer a missing record gets.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * Constructor for EmployeeController.
     * @param employeeService the service for employee operations
     */
    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * List the employees in the caller's brand, one page at a time.
     * @param page the zero-based page to serve, or null for the first
     * @param size the requested page size, or null for the default
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the requested page of that brand's employees
     */
    @GetMapping
    public PageResponse<EmployeeResponse> getEmployees(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        return PageResponse.from(
                employeeService.getAllEmployees(principal.organizationId(),
                        PageRequests.of(page, size).toPageable()),
                EmployeeResponse::from);
    }

    /**
     * Get an employee by ID, within the caller's brand.
     * @param id the employee ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the employee
     * @throws EmployeeNotFoundException if no employee exists with the ID in
     *         the caller's brand
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getEmployee(
            @PathVariable Long id,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        Employee employee = employeeService.getEmployeeById(id, principal.organizationId())
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        return ResponseEntity.ok(EmployeeResponse.from(employee));
    }

    /**
     * Create a new employee. The payload's organization must be the
     * caller's brand.
     * @param request the validated employee creation payload
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the created employee
     */
    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody EmployeeRequest request,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        Employee saved = employeeService.createEmployee(request.toEntity(),
                request.organizationId(), principal.organizationId());
        return ResponseEntity.status(201).body(EmployeeResponse.from(saved));
    }

    /**
     * Update an existing employee, within the caller's brand.
     * @param id the employee ID
     * @param request the replacement values
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the updated employee
     */
    @PutMapping("/{id}")
    public EmployeeResponse updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        Employee updated = employeeService.updateEmployee(id, request.toEntity(),
                request.organizationId(), principal.organizationId());
        return EmployeeResponse.from(updated);
    }

    /**
     * Delete an employee by ID, within the caller's brand.
     * @param id the employee ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(
            @PathVariable Long id,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        employeeService.deleteEmployee(id, principal.organizationId());
        return ResponseEntity.noContent().build();
    }
}
