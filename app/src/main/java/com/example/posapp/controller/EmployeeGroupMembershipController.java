package com.example.posapp.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.EmployeeGroupResponse;
import com.example.posapp.dto.EmployeeResponse;
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
 *       employee's groups.</li>
 *   <li>{@code GET /api/v1/employee-groups/{groupId}/employees} lists the
 *       group's employees.</li>
 * </ul>
 * Missing aggregates return 404 and cross-organization writes return 400
 * through the exceptions mapped by
 * {@link com.example.posapp.exception.ApiExceptionHandler}.
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
     * @return 204 No Content
     */
    @PutMapping("/api/v1/employees/{employeeId}/groups/{groupId}")
    public ResponseEntity<Void> addMembership(
            @PathVariable Long employeeId,
            @PathVariable Long groupId) {
        membershipService.addMembership(employeeId, groupId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Remove an employee from an employee group. Idempotent: detaching a
     * pair that is not attached is treated as success. Neither the
     * employee nor the group is deleted.
     * @param employeeId the employee ID
     * @param groupId the employee group ID
     * @return 204 No Content
     */
    @DeleteMapping("/api/v1/employees/{employeeId}/groups/{groupId}")
    public ResponseEntity<Void> removeMembership(
            @PathVariable Long employeeId,
            @PathVariable Long groupId) {
        membershipService.removeMembership(employeeId, groupId);
        return ResponseEntity.noContent().build();
    }

    /**
     * List the employee groups an employee belongs to.
     * @param employeeId the employee ID
     * @return the groups attached to the employee
     */
    @GetMapping("/api/v1/employees/{employeeId}/groups")
    public List<EmployeeGroupResponse> listGroupsForEmployee(@PathVariable Long employeeId) {
        return membershipService.listGroupsForEmployee(employeeId).stream()
                .map(EmployeeGroupResponse::from)
                .toList();
    }

    /**
     * List the employees belonging to an employee group.
     * @param groupId the employee group ID
     * @return the employees attached to the group
     */
    @GetMapping("/api/v1/employee-groups/{groupId}/employees")
    public List<EmployeeResponse> listEmployeesForGroup(@PathVariable Long groupId) {
        return membershipService.listEmployeesForGroup(groupId).stream()
                .map(EmployeeResponse::from)
                .toList();
    }
}
