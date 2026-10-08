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

import com.example.posapp.dto.ModifierRequest;
import com.example.posapp.dto.ModifierResponse;
import com.example.posapp.entity.Modifier;
import com.example.posapp.exception.ModifierNotFoundException;
import com.example.posapp.service.ModifierService;

import jakarta.validation.Valid;

/**
 * REST controller for standalone {@link Modifier} CRUD.
 * <p>
 * Modifiers are reusable across modifier groups; assignment lifecycle
 * lives under {@link ModifierGroupController}'s sub-resource
 * {@code /api/v1/modifier-groups/{id}/modifiers}.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/modifiers")
public class ModifierController {

    private final ModifierService modifierService;

    /**
     * Constructor for ModifierController.
     * @param modifierService the service for modifier operations
     */
    public ModifierController(ModifierService modifierService) {
        this.modifierService = modifierService;
    }

    /**
     * List every modifier.
     * @return all modifiers as API responses
     */
    @GetMapping
    public List<ModifierResponse> getModifiers() {
        return modifierService.getAllModifiers().stream()
                .map(ModifierResponse::from)
                .toList();
    }

    /**
     * Get a modifier by ID.
     * @param id the modifier ID
     * @return the modifier
     * @throws ModifierNotFoundException if no modifier exists with the ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ModifierResponse> getModifier(@PathVariable Long id) {
        Modifier modifier = modifierService.getModifierById(id)
                .orElseThrow(() -> new ModifierNotFoundException(id));
        return ResponseEntity.ok(ModifierResponse.from(modifier));
    }

    /**
     * Create a new modifier.
     * @param request the validated modifier payload
     * @return the created modifier
     */
    @PostMapping
    public ResponseEntity<ModifierResponse> createModifier(@Valid @RequestBody ModifierRequest request) {
        Modifier saved = modifierService.createModifier(request.toEntity());
        return ResponseEntity.status(201).body(ModifierResponse.from(saved));
    }

    /**
     * Update an existing modifier's name, price adjustment, and active flag.
     * @param id the modifier ID
     * @param request the replacement values
     * @return the updated modifier
     */
    @PutMapping("/{id}")
    public ModifierResponse updateModifier(
            @PathVariable Long id,
            @Valid @RequestBody ModifierRequest request) {
        return ModifierResponse.from(modifierService.updateModifier(id, request.toEntity()));
    }

    /**
     * Delete a modifier by ID. Modifiers still assigned to a group are
     * rejected with 400.
     * @param id the modifier ID
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteModifier(@PathVariable Long id) {
        modifierService.deleteModifier(id);
        return ResponseEntity.noContent().build();
    }
}
