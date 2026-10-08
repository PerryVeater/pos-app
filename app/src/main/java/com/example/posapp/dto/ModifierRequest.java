package com.example.posapp.dto;

import java.math.BigDecimal;

import com.example.posapp.entity.Modifier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating or updating a modifier over HTTP.
 * <p>
 * Enforced at the API boundary: the name must be provided and non-blank,
 * and it is capped at 255 characters to match the underlying column. The
 * price adjustment is required and must be an exact {@code BigDecimal}
 * (positive, zero, or negative). {@code active} is optional; omitted means
 * the modifier is active.
 * </p>
 *
 * @param name the modifier name
 * @param priceAdjustment the exact monetary adjustment applied when selected
 * @param active whether the modifier is currently offered (defaults to true)
 */
public record ModifierRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull BigDecimal priceAdjustment,
        Boolean active) {

    /**
     * Map the request to a transient {@link Modifier} entity for the service layer.
     * @return a new modifier with the request values and no ID
     */
    public Modifier toEntity() {
        Modifier modifier = new Modifier(name, priceAdjustment);
        modifier.setActive(active == null || active);
        return modifier;
    }
}
