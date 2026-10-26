package com.example.posapp.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication for a valid token whose subject resolves to no employee.
 * <p>
 * The token was signed by the configured provider, is addressed to this API and
 * has not expired, so it <em>is</em> an authentication. What it lacks is a
 * domain principal: no employee is linked to the subject, or the linked
 * employee has been deactivated. There is no organization to scope the request
 * to, so every management endpoint must refuse it.
 * </p>
 * <p>
 * That refusal is produced by carrying no authorities at all rather than by
 * throwing during conversion. The converter runs inside the bearer-token filter,
 * upstream of the filter that translates exceptions into responses, so an
 * exception raised there is not a 403 - it leaves the chain as an error.
 * Authorities, by contrast, are read by the authorization filter, whose denials
 * are translated. The consequence for the wire is exactly the intended one: the
 * same 403 as an employee who lacks the role.
 * </p>
 * <p>
 * This type exists so the two cases stay distinguishable in logs, where an
 * operator needs to know whether to link an account or grant it a role.
 * </p>
 */
public final class UnresolvedIdentityAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final transient String subject;

    /**
     * Create an authenticated token with no authorities.
     * @param subject the token's {@code sub} claim
     */
    public UnresolvedIdentityAuthentication(String subject) {
        super(List.of());
        this.subject = subject;
        setAuthenticated(true);
    }

    /**
     * Credentials are never retained for a bearer token.
     * @return an empty string
     */
    @Override
    public Object getCredentials() {
        return "";
    }

    /**
     * Get the subject, which is all that is known about the caller.
     * @return the token subject, possibly {@code null}
     */
    @Override
    public Object getPrincipal() {
        return subject;
    }

    /**
     * Name the authentication after the subject.
     * <p>
     * A {@link BackOfficePrincipal} names a person; here there is no person to
     * name and the subject is the only handle an operator has for finding the
     * unlinked account.
     * </p>
     * @return the token subject
     */
    @Override
    public String getName() {
        return subject == null ? "" : subject;
    }

    /**
     * Get the granted authorities, which are fixed at none.
     * @return an empty collection
     */
    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return List.of();
    }
}
