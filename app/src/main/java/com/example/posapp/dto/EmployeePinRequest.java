package com.example.posapp.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for setting or changing an employee's POS PIN.
 * <p>
 * The PIN travels as a JSON string, never a number, so leading zeros survive
 * deserialization ({@code "0421"} stays {@code "0421"}). Only presence is
 * enforced here; the authoritative shape rules - digits only and the
 * configured {@code minLength}..{@code maxLength} window - live in
 * {@link com.example.posapp.service.EmployeePinService} against
 * {@link com.example.posapp.service.PinProperties}. Keeping the length
 * window in one place means a deployment that widens
 * {@code pos.pin.max-length} is honoured rather than rejected by a stale
 * boundary annotation, and every malformed PIN surfaces through the same
 * 400 {@link com.example.posapp.exception.EmployeePinValidationException}.
 * </p>
 * <p>
 * The value is never trimmed, normalized, or case-folded: it is used
 * verbatim, and nothing in this record is echoed back in a response.
 * </p>
 *
 * @param pin the numeric POS PIN as submitted
 */
public record EmployeePinRequest(
        @NotBlank String pin) {
}
