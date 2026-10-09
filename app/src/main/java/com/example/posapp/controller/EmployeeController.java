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

import com.example.posapp.dto.EmployeeRequest;
import com.example.posapp.dto.EmployeeResponse;
import com.example.posapp.entity.Employee;
import com.example.posapp.exception.EmployeeNotFoundException;
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
     * List all employees.
     * @return every employee as an API response
     */
    @GetMapping
    public List<EmployeeResponse> getEmployees() {
        return employeeService.getAllEmployees().stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    /**
     * Get an employee by ID.
     * @param id the employee ID
     * @return the employee
     * @throws EmployeeNotFoundException if no employee exists with the ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> getEmployee(@PathVariable Long id) {
        Employee employee = employeeService.getEmployeeById(id)
                .orElseThrow(() -> new EmployeeNotFoundException(id));
        return ResponseEntity.ok(EmployeeResponse.from(employee));
    }

    /**
     * Create a new employee owned by the specified organization.
     * @param request the validated employee creation payload
     * @return the created employee
     */
    @PostMapping
    public ResponseEntity<EmployeeResponse> createEmployee(
            @Valid @RequestBody EmployeeRequest request) {
        Employee saved = employeeService.createEmployee(request.toEntity(), request.organizationId());
        return ResponseEntity.status(201).body(EmployeeResponse.from(saved));
    }

    /**
     * Update an existing employee, including moving it to a different
     * organization.
     * @param id the employee ID
     * @param request the replacement values
     * @return the updated employee
     */
    @PutMapping("/{id}")
    public EmployeeResponse updateEmployee(
            @PathVariable Long id,
            @Valid @RequestBody EmployeeRequest request) {
        Employee updated = employeeService.updateEmployee(id, request.toEntity(), request.organizationId());
        return EmployeeResponse.from(updated);
    }

    /**
     * Delete an employee by ID.
     * @param id the employee ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}
