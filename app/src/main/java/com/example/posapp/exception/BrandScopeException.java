package com.example.posapp.exception;

/**
 * Thrown when an action lies outside every brand and is therefore not
 * available to a back-office caller.
 * <p>
 * Brand scoping confines a principal to one organization, so an action that
 * would create or destroy an organization is not merely out of scope for it -
 * there is no brand it could be performed in. Such requests are refused with
 * {@code 403} rather than the {@code 404} that out-of-scope records use,
 * because no record is being named and pretending a route does not exist
 * would hide a capability that a platform-level role is meant to own.
 * </p>
 * <p>
 * Distinct from {@link OrganizationNotFoundException}, which says the named
 * organization does not exist within the caller's brand.
 * </p>
 */
public class BrandScopeException extends RuntimeException {

    /**
     * Constructor with the action that was refused.
     * @param action a phrase naming the refused action, used verbatim in the
     *        error detail, for example {@code "creating an organization"}
     */
    public BrandScopeException(String action) {
        super("Outside the brand scope: " + action);
    }
}
