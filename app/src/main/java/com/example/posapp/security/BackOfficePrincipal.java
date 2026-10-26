package com.example.posapp.security;

/**
 * The resolved identity behind an authenticated back-office request.
 * <p>
 * A token proves only <em>who</em> is calling. This record is what the
 * application then knows about them: the OIDC subject from the token and the
 * {@code employee} row that subject is linked to, which supplies the
 * organization every management endpoint is confined to. The organization is
 * read from the employee rather than from a token claim so there is exactly one
 * source of truth for it - see the Flyway {@code V25} header.
 * </p>
 * <p>
 * Fields:
 * <ul>
 *   <li>{@code subject} - OIDC {@code sub} claim, stable for the account.</li>
 *   <li>{@code employeeId} - the linked employee's identifier.</li>
 *   <li>{@code organizationId} - the linked employee's organization; the
 *       brand scope for this request.</li>
 *   <li>{@code employeeName} - the linked employee's display name.</li>
 * </ul>
 * </p>
 * <p>
 * Instances are created once per request by {@link BackOfficeIdentityService}
 * after the token has been validated, and are immutable.
 * </p>
 *
 * @param subject the identity provider subject
 * @param employeeId the linked employee identifier
 * @param organizationId the brand this request may act within
 * @param employeeName the linked employee's display name
 */
public record BackOfficePrincipal(String subject, Long employeeId, Long organizationId, String employeeName) {

    /**
     * Return a representation that identifies the caller without exposing the
     * subject. Used in log lines and error messages.
     * @return a short description of the principal
     */
    @Override
    public String toString() {
        return "BackOfficePrincipal{employeeId=" + employeeId
                + ", organizationId=" + organizationId + "}";
    }
}
