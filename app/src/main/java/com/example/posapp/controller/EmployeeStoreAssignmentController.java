package com.example.posapp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.EmployeeResponse;
import com.example.posapp.dto.StoreResponse;
import com.example.posapp.security.BackOfficePrincipal;
import com.example.posapp.service.EmployeeStoreAssignmentService;

/**
 * REST controller for the Employee ↔ Store assignment sub-resources.
 * <p>
 * Kept separate from {@link EmployeeController} and
 * {@link StoreController} so the existing aggregates' APIs stay untouched,
 * per the project convention of exposing new assignment layers through
 * dedicated controllers (see {@link EmployeeGroupMembershipController}).
 * The four endpoints span two base paths so the class intentionally
 * carries no {@code @RequestMapping} prefix and each method spells out its
 * full route:
 * <ul>
 *   <li>{@code PUT /api/v1/employees/{employeeId}/stores/{storeId}} adds
 *       an assignment and is idempotent: assigning a pair that is already
 *       assigned returns 204 without creating a duplicate row.</li>
 *   <li>{@code DELETE /api/v1/employees/{employeeId}/stores/{storeId}}
 *       removes an assignment and is idempotent: unassigning a pair that
 *       is not assigned returns 204 without an error.</li>
 *   <li>{@code GET /api/v1/employees/{employeeId}/stores} lists the
 *       employee's stores.</li>
 *   <li>{@code GET /api/v1/stores/{storeId}/employees} lists the store's
 *       employees.</li>
 * </ul>
 * Missing aggregates return 404 through the exceptions mapped by
 * {@link com.example.posapp.exception.ApiExceptionHandler}. Every route is
 * confined to the caller's brand: an employee or store belonging to another
 * brand is reported as missing, so an assignment cannot be read or written
 * across brands.
 * </p>
 */
@RestController
public class EmployeeStoreAssignmentController {

    private final EmployeeStoreAssignmentService assignmentService;

    /**
     * Constructor for EmployeeStoreAssignmentController.
     * @param assignmentService the service for assignment operations
     */
    public EmployeeStoreAssignmentController(EmployeeStoreAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /**
     * Assign an employee to a store. Idempotent: assigning a pair that is
     * already assigned is treated as success.
     * @param employeeId the employee ID
     * @param storeId the store ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return 204 No Content
     */
    @PutMapping("/api/v1/employees/{employeeId}/stores/{storeId}")
    public ResponseEntity<Void> assignEmployeeToStore(
            @PathVariable Long employeeId,
            @PathVariable Long storeId,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        assignmentService.assignEmployeeToStore(employeeId, storeId, principal.organizationId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Remove an employee's assignment to a store. Idempotent: unassigning a
     * pair that is not assigned is treated as success. Neither the
     * employee nor the store is deleted.
     * @param employeeId the employee ID
     * @param storeId the store ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return 204 No Content
     */
    @DeleteMapping("/api/v1/employees/{employeeId}/stores/{storeId}")
    public ResponseEntity<Void> removeEmployeeFromStore(
            @PathVariable Long employeeId,
            @PathVariable Long storeId,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        assignmentService.removeEmployeeFromStore(employeeId, storeId, principal.organizationId());
        return ResponseEntity.noContent().build();
    }

    /**
     * List the stores an employee is assigned to, within the caller's brand.
     * @param employeeId the employee ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the stores assigned to the employee
     */
    @GetMapping("/api/v1/employees/{employeeId}/stores")
    public List<StoreResponse> listStoresForEmployee(
            @PathVariable Long employeeId,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        return assignmentService.listStoresForEmployee(employeeId, principal.organizationId()).stream()
                .map(StoreResponse::from)
                .toList();
    }

    /**
     * List the employees assigned to a store, within the caller's brand.
     * @param storeId the store ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the employees assigned to the store
     */
    @GetMapping("/api/v1/stores/{storeId}/employees")
    public List<EmployeeResponse> listEmployeesForStore(
            @PathVariable Long storeId,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        return assignmentService.listEmployeesForStore(storeId, principal.organizationId()).stream()
                .map(EmployeeResponse::from)
                .toList();
    }
}
