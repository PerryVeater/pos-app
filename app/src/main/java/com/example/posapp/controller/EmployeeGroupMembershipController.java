package com.example.posapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.EmployeeGroupResponse;
import com.example.posapp.dto.EmployeeResponse;
import com.example.posapp.dto.PageRequests;
import com.example.posapp.dto.PageResponse;
import com.example.posapp.security.BackOfficePrincipal;
import com.example.posapp.service.EmployeeGroupMembershipService;

/**
 * REST controller for the Employee ↔ EmployeeGroup membership
 * sub-resources.
 * <p>
 * Kept separate from {@link EmployeeController} and
 * {@link EmployeeGroupController} so the existing aggregates' APIs stay
 * untouched, per the project convention of exposing new assignment layers
 * through dedicated controllers (see
 * {@link MenuItemModifierGroupController}). The four endpoints span two
 * base paths so the class intentionally carries no {@code @RequestMapping}
 * prefix and each method spells out its full route:
 * <ul>
 *   <li>{@code PUT /api/v1/employees/{employeeId}/groups/{groupId}} adds
 *       a membership and is idempotent: attaching a pair that is already
 *       attached returns 204 without creating a duplicate row.</li>
 *   <li>{@code DELETE /api/v1/employees/{employeeId}/groups/{groupId}}
 *       removes a membership and is idempotent: detaching a pair that is
 *       not attached returns 204 without an error.</li>
 *   <li>{@code GET /api/v1/employees/{employeeId}/groups} lists the
 *       employee's groups, one page at a time.</li>
 *   <li>{@code GET /api/v1/employee-groups/{groupId}/employees} lists the
 *       group's employees, one page at a time.</li>
 * </ul>
 * Missing aggregates return 404 through the exceptions mapped by
 * {@link com.example.posapp.exception.ApiExceptionHandler}. Every route is
 * confined to the caller's brand: an employee or group belonging to another
 * brand is reported as missing, so a membership cannot be read or written
 * across brands.
 * </p>
 */
@RestController
public class EmployeeGroupMembershipController {

    private final EmployeeGroupMembershipService membershipService;

    /**
     * Constructor for EmployeeGroupMembershipController.
     * @param membershipService the service for membership operations
     */
    public EmployeeGroupMembershipController(EmployeeGroupMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    /**
     * Add an employee to an employee group. Idempotent: attaching a pair
     * that is already attached is treated as success.
     * @param employeeId the employee ID
     * @param groupId the employee group ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return 204 No Content
     */
    @PutMapping("/api/v1/employees/{employeeId}/groups/{groupId}")
    public ResponseEntity<Void> addMembership(
            @PathVariable Long employeeId,
            @PathVariable Long groupId,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        membershipService.addMembership(employeeId, groupId, principal.organizationId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Remove an employee from an employee group. Idempotent: detaching a
     * pair that is not attached is treated as success. Neither the
     * employee nor the group is deleted.
     * @param employeeId the employee ID
     * @param groupId the employee group ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return 204 No Content
     */
    @DeleteMapping("/api/v1/employees/{employeeId}/groups/{groupId}")
    public ResponseEntity<Void> removeMembership(
            @PathVariable Long employeeId,
            @PathVariable Long groupId,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        membershipService.removeMembership(employeeId, groupId, principal.organizationId());
        return ResponseEntity.noContent().build();
    }

    /**
     * List the employee groups an employee belongs to, within the caller's
     * brand, one page at a time.
     * @param employeeId the employee ID
     * @param page the zero-based page to serve, or null for the first
     * @param size the requested page size, or null for the default
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the requested page of groups attached to the employee
     */
    @GetMapping("/api/v1/employees/{employeeId}/groups")
    public PageResponse<EmployeeGroupResponse> listGroupsForEmployee(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        return PageResponse.from(membershipService.listGroupsForEmployee(
                        employeeId, principal.organizationId(),
                        PageRequests.of(page, size).toPageable()))
                .map(row -> EmployeeGroupResponse.from(row.getEmployeeGroup()));
    }

    /**
     * List the employees belonging to an employee group, within the
     * caller's brand, one page at a time.
     * @param groupId the employee group ID
     * @param page the zero-based page to serve, or null for the first
     * @param size the requested page size, or null for the default
     * @param principal the authenticated caller, whose brand scopes the request
     * @return the requested page of employees attached to the group
     */
    @GetMapping("/api/v1/employee-groups/{groupId}/employees")
    public PageResponse<EmployeeResponse> listEmployeesForGroup(
            @PathVariable Long groupId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        return PageResponse.from(membershipService.listEmployeesForGroup(
                        groupId, principal.organizationId(),
                        PageRequests.of(page, size).toPageable()))
                .map(row -> EmployeeResponse.from(row.getEmployee()));
    }
}
