package com.example.posapp.security;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns a validated {@link Jwt} into an authentication.
 * <p>
 * Two things happen here, and nothing else: the subject is resolved to an
 * employee (which also fixes the brand scope for the request), and the
 * provider's realm roles become Spring Security authorities. No claim is
 * trusted as an authorization fact beyond the roles the provider issued.
 * </p>
 * <p>
 * Roles are read from Keycloak's {@code realm_access.roles} structure. They are
 * mapped with the {@code ROLE_} prefix so the coarse back-office gate can be
 * written as {@code hasRole("BRAND_ADMIN")}. This is not the permission model:
 * M4 introduces job roles and a permission catalogue in the database, and this
 * mapping only answers "may this account enter the back office at all".
 * </p>
 */
public class BackOfficeAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /** Claim holding Keycloak's realm-level role assignments. */
    private static final String REALM_ACCESS_CLAIM = "realm_access";

    /** Entry within {@code realm_access} that lists the realm role names. */
    private static final String ROLES_ENTRY = "roles";

    /** Authority prefix Spring Security expects for {@code hasRole(...)}. */
    private static final String ROLE_PREFIX = "ROLE_";

    private final BackOfficeIdentityService identityService;

    /**
     * Constructor for BackOfficeAuthenticationConverter.
     * @param identityService the identity-to-employee resolver
     */
    public BackOfficeAuthenticationConverter(BackOfficeIdentityService identityService) {
        this.identityService = identityService;
    }

    /**
     * Convert a validated token into an authentication for its employee.
     * @param jwt the validated access token
     * @return the authentication carrying the resolved principal, or one with
     *         no authorities when the subject resolves to no active employee
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Optional<BackOfficePrincipal> principal = identityService.resolve(jwt.getSubject());
        if (principal.isEmpty()) {
            return new UnresolvedIdentityAuthentication(jwt.getSubject());
        }
        return new BackOfficeAuthentication(principal.get(), authorities(jwt));
    }

    /**
     * Extract realm roles from the token as {@code ROLE_}-prefixed authorities.
     * @param jwt the validated access token
     * @return the granted authorities, empty when the token carries no roles
     */
    private static Collection<GrantedAuthority> authorities(Jwt jwt) {
        Object claim = jwt.getClaim(REALM_ACCESS_CLAIM);
        if (!(claim instanceof Map<?, ?> realmAccess)) {
            return List.of();
        }
        if (!(realmAccess.get(ROLES_ENTRY) instanceof Collection<?> roles)) {
            return List.of();
        }
        Set<GrantedAuthority> authorities = roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + normalize(role)))
                .collect(Collectors.toUnmodifiableSet());
        return authorities;
    }

    /**
     * Turn a provider role name into the authority suffix Spring Security
     * expects.
     * <p>
     * Keycloak role names are lower case and hyphenated ({@code brand-admin}),
     * while {@code hasRole("BRAND_ADMIN")} looks for {@code ROLE_BRAND_ADMIN}:
     * Spring prefixes the given name with {@code ROLE_} and changes nothing
     * else, so a hyphen would never match. Normalizing here keeps the mapping
     * in one place instead of making every {@code hasRole} guess the spelling.
     * </p>
     * @param role the provider's role name
     * @return the name upper-cased with hyphens replaced by underscores
     */
    private static String normalize(String role) {
        return role.toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
