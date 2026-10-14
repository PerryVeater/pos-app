package com.example.posapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POS PIN authentication.
 * <p>
 * Only structural presence is validated at the boundary: {@code storeId}
 * must be provided and {@code pin} must be a non-blank string. The PIN is
 * treated as an opaque string and used verbatim - it is never trimmed or
 * normalized, so leading zeros are preserved. Deliberate note: the PIN's
 * digit-length policy is <em>not</em> re-checked here, because rejecting an
 * oddly shaped PIN with a distinct validation error would leak information
 * through an inconsistent response. All credential-shaped failures funnel to
 * one generic authentication error in the service, so this DTO stays quiet
 * about format.
 * </p>
 *
 * @param storeId the store the employee is authenticating at
 * @param pin the PIN as submitted, used verbatim
 */
public record PosAuthenticateRequest(
        @NotNull Long storeId,
        @NotBlank String pin) {
}
