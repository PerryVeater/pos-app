package com.example.posapp.controller;

import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.posapp.security.BackOfficePrincipal;

/**
 * Installs the back-office identity the brand-scoped controller slices need.
 * <p>
 * The management controllers resolve the caller's brand through
 * {@code @AuthenticationPrincipal}, which reads {@link SecurityContextHolder}.
 * The slices run with {@code addFilters = false} so the security filter chain
 * never runs and nothing would populate the context; each slice therefore
 * calls {@link #signIn()} (or {@link #signIn(long)} for a second brand) in a
 * {@code @BeforeEach} and {@link #signOut()} in a {@code @AfterEach}.
 * </p>
 */
final class BrandScopeTestSupport {

    /** The brand every slice test acts within unless it says otherwise. */
    static final long BRAND_ID = 1L;

    private static final long EMPLOYEE_ID = 7L;

    private BrandScopeTestSupport() {
    }

    /**
     * Act as an administrator of {@link #BRAND_ID}.
     */
    static void signIn() {
        signIn(BRAND_ID);
    }

    /**
     * Act as an administrator of the given brand.
     * @param brandId the brand the caller may manage
     */
    static void signIn(long brandId) {
        BackOfficePrincipal principal =
                new BackOfficePrincipal("subject-" + brandId, EMPLOYEE_ID, brandId, "Test Admin");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_BRAND_ADMIN"))));
    }

    /**
     * Drop the identity so the next test starts unauthenticated.
     */
    static void signOut() {
        SecurityContextHolder.clearContext();
    }
}
