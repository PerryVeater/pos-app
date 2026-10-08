package com.example.posapp.dto;

import java.math.BigDecimal;

import com.example.posapp.entity.Modifier;

/**
 * API representation of a modifier returned by the modifier endpoints.
 *
 * @param id the modifier ID
 * @param name the modifier name
 * @param priceAdjustment the exact monetary adjustment
 * @param active whether the modifier is currently offered
 */
public record ModifierResponse(
        Long id,
        String name,
        BigDecimal priceAdjustment,
        boolean active) {

    /**
     * Map a {@link Modifier} entity to its API representation.
     * @param modifier the entity to map
     * @return the API representation
     */
    public static ModifierResponse from(Modifier modifier) {
        return new ModifierResponse(
                modifier.getId(),
                modifier.getName(),
                modifier.getPriceAdjustment(),
                modifier.isActive());
    }
}
