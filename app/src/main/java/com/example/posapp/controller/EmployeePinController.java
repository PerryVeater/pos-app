package com.example.posapp.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.posapp.dto.EmployeePinRequest;
import com.example.posapp.security.BackOfficePrincipal;
import com.example.posapp.service.EmployeePinService;

import jakarta.validation.Valid;

/**
 * REST controller for the employee POS PIN credential sub-resource.
 * <p>
 * PIN material is strictly write-only over HTTP: there is deliberately no
 * GET endpoint, and both operations return 204 with an empty body so no
 * hash, fingerprint, or echo of the submitted PIN can leak through a
 * response. Routes follow the existing assignment sub-resource convention
 * under {@code /api/v1/employees/{employeeId}}:
 * <ul>
 *   <li>{@code PUT /api/v1/employees/{employeeId}/pin} sets or changes
 *       the PIN from the JSON body (the PIN is a string so leading zeros
 *       survive).</li>
 *   <li>{@code DELETE /api/v1/employees/{employeeId}/pin} removes the
 *       PIN and frees it at every assigned store; it is idempotent.</li>
 * </ul>
 * Missing employees return 404 and malformed PINs, inactive employees, and
 * store-scoped uniqueness conflicts return 400 through
 * {@link com.example.posapp.exception.ApiExceptionHandler}. Login,
 * sessions, and token issuance are out of scope for this feature.
 * </p>
 * <p>
 * Both routes are confined to the caller's brand: an employee belonging to
 * another brand is reported as missing, so no PIN can be set or cleared
 * across brands.
 * </p>
 */
@RestController
public class EmployeePinController {

    private final EmployeePinService pinService;

    /**
     * Constructor for EmployeePinController.
     * @param pinService the service for PIN lifecycle operations
     */
    public EmployeePinController(EmployeePinService pinService) {
        this.pinService = pinService;
    }

    /**
     * Set or change an employee's POS PIN.
     * @param employeeId the employee ID
     * @param request the request body carrying the new PIN
     * @param principal the authenticated caller, whose brand scopes the request
     * @return 204 No Content, with no representation of the credential
     */
    @PutMapping("/api/v1/employees/{employeeId}/pin")
    public ResponseEntity<Void> setPin(
            @PathVariable Long employeeId,
            @Valid @RequestBody EmployeePinRequest request,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        pinService.setPin(employeeId, request.pin(), principal.organizationId());
        return ResponseEntity.noContent().build();
    }

    /**
     * Remove an employee's POS PIN. Idempotent: removing a PIN the
     * employee does not have is treated as success.
     * @param employeeId the employee ID
     * @param principal the authenticated caller, whose brand scopes the request
     * @return 204 No Content
     */
    @DeleteMapping("/api/v1/employees/{employeeId}/pin")
    public ResponseEntity<Void> removePin(
            @PathVariable Long employeeId,
            @AuthenticationPrincipal BackOfficePrincipal principal) {
        pinService.removePin(employeeId, principal.organizationId());
        return ResponseEntity.noContent().build();
    }
}
