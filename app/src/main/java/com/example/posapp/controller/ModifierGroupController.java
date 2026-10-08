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

import com.example.posapp.dto.ModifierAssignmentRequest;
import com.example.posapp.dto.ModifierAssignmentResponse;
import com.example.posapp.dto.ModifierGroupRequest;
import com.example.posapp.dto.ModifierGroupResponse;
import com.example.posapp.entity.ModifierGroup;
import com.example.posapp.exception.ModifierGroupNotFoundException;
import com.example.posapp.service.ModifierGroupService;

import jakarta.validation.Valid;

/**
 * REST controller for modifier group operations, including the sub-resource
 * for assigning and unassigning reusable {@link com.example.posapp.entity.Modifier}s.
 * <p>
 * Business rules are enforced by {@link ModifierGroupService}. The HTTP
 * contract is decoupled from the JPA model via {@link ModifierGroupRequest}
 * and {@link ModifierGroupResponse}.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/modifier-groups")
public class ModifierGroupController {

    private final ModifierGroupService modifierGroupService;

    /**
     * Constructor for ModifierGroupController.
     * @param modifierGroupService the service for modifier group operations
     */
    public ModifierGroupController(ModifierGroupService modifierGroupService) {
        this.modifierGroupService = modifierGroupService;
    }

    /**
     * List all modifier groups. Assignments are returned empty in the
     * collection view to avoid an N+1 query; use
     * {@code GET /api/v1/modifier-groups/{id}} for the full detail.
     * @return every modifier group as an API response
     */
    @GetMapping
    public List<ModifierGroupResponse> getModifierGroups() {
        return modifierGroupService.getAllModifierGroups().stream()
                .map(group -> ModifierGroupResponse.from(group, List.of()))
                .toList();
    }

    /**
     * Get a modifier group by ID, including its assigned modifiers in
     * display order.
     * @param id the modifier group ID
     * @return the group with its assignments
     * @throws ModifierGroupNotFoundException if the group does not exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<ModifierGroupResponse> getModifierGroup(@PathVariable Long id) {
        ModifierGroup group = modifierGroupService.getModifierGroupById(id)
                .orElseThrow(() -> new ModifierGroupNotFoundException(id));
        return ResponseEntity.ok(ModifierGroupResponse.from(
                group, modifierGroupService.listModifierAssignments(id)));
    }

    /**
     * Create a new modifier group.
     * @param request the validated group creation payload
     * @return the created group (empty modifiers list)
     */
    @PostMapping
    public ResponseEntity<ModifierGroupResponse> createModifierGroup(
            @Valid @RequestBody ModifierGroupRequest request) {
        ModifierGroup saved = modifierGroupService.createModifierGroup(request.toEntity());
        return ResponseEntity.status(201).body(ModifierGroupResponse.from(saved, List.of()));
    }

    /**
     * Update an existing modifier group.
     * @param id the modifier group ID
     * @param request the replacement values
     * @return the updated group
     */
    @PutMapping("/{id}")
    public ModifierGroupResponse updateModifierGroup(
            @PathVariable Long id,
            @Valid @RequestBody ModifierGroupRequest request) {
        ModifierGroup updated = modifierGroupService.updateModifierGroup(id, request.toEntity());
        return ModifierGroupResponse.from(updated, modifierGroupService.listModifierAssignments(id));
    }

    /**
     * Delete a modifier group by ID. Attached assignments are removed via
     * the JPA cascade; the referenced modifiers stay intact.
     * @param id the modifier group ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteModifierGroup(@PathVariable Long id) {
        modifierGroupService.deleteModifierGroup(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * List the modifiers assigned to a group, ordered by display position.
     * @param id the modifier group ID
     * @return the ordered assignments
     */
    @GetMapping("/{id}/modifiers")
    public List<ModifierAssignmentResponse> listModifiers(@PathVariable Long id) {
        return modifierGroupService.listModifierAssignments(id).stream()
                .map(ModifierAssignmentResponse::from)
                .toList();
    }

    /**
     * Assign a modifier to a group with the given display order.
     * @param id the modifier group ID
     * @param request the assignment payload (modifierId and displayOrder)
     * @return the created assignment
     */
    @PostMapping("/{id}/modifiers")
    public ResponseEntity<ModifierAssignmentResponse> assignModifier(
            @PathVariable Long id,
            @Valid @RequestBody ModifierAssignmentRequest request) {
        return ResponseEntity.status(201).body(ModifierAssignmentResponse.from(
                modifierGroupService.assignModifier(id, request.modifierId(), request.displayOrder())));
    }

    /**
     * Unassign a modifier from a group. The underlying modifier is not deleted.
     * @param id the modifier group ID
     * @param modifierId the modifier ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}/modifiers/{modifierId}")
    public ResponseEntity<Void> unassignModifier(
            @PathVariable Long id,
            @PathVariable Long modifierId) {
        modifierGroupService.unassignModifier(id, modifierId);
        return ResponseEntity.noContent().build();
    }
}
