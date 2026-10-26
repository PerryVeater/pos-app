package com.example.posapp.security;

import java.util.Collection;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication produced for a validated back-office access token.
 * <p>
 * The principal is a {@link BackOfficePrincipal} rather than the raw
 * {@code Jwt}: by the time an authentication object exists, the token has been
 * reduced to the facts the application is allowed to act on. Controllers read
 * the organization from it; nothing downstream should need to parse claims
 * again.
 * </p>
 */
public final class BackOfficeAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final transient BackOfficePrincipal principal;

    /**
     * Create an authenticated token for a resolved principal.
     * @param principal the resolved identity
     * @param authorities the granted roles, already prefixed
     */
    public BackOfficeAuthentication(BackOfficePrincipal principal,
                                    Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    /**
     * Credentials are never retained for a bearer token: the token itself is
     * the credential and it has already been validated.
     * @return an empty string
     */
    @Override
    public Object getCredentials() {
        return "";
    }

    /**
     * Get the resolved principal.
     * @return the principal behind this request
     */
    @Override
    public Object getPrincipal() {
        return principal;
    }

    /**
     * Name the authentication after the employee rather than the token subject,
     * so audit lines and debug output identify a person, not an opaque id.
     * @return the employee's display name
     */
    @Override
    public String getName() {
        return principal.employeeName();
    }
}
