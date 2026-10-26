package com.example.posapp.security;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.DispatcherType;

/**
 * The boundary between the two authenticated surfaces of this application.
 * <p>
 * They are deliberately separate and share nothing:
 * </p>
 * <ul>
 *   <li><strong>POS</strong> - {@code /api/v1/pos/**} plus {@code /health}.
 *       Authentication is an opaque, database-backed session token exchanged
 *       for a PIN at a store; it is validated by the service layer, so the
 *       filter chain only has to stay out of the way. A JWT is not accepted
 *       here, because nothing in this chain can verify one.</li>
 *   <li><strong>Back office</strong> - everything else. A JWT access token from
 *       the configured OIDC provider is required, its subject must be linked to
 *       an active employee ({@code employee.idp_subject}), and that account must
 *       carry the {@code brand-admin} realm role.</li>
 * </ul>
 * <p>
 * Neither credential can be replayed against the other surface, and that is a
 * property of the design rather than of a check: the POS token space is opaque
 * random material that no JWT decoder can validate, and the management decoder
 * only ever sees tokens signed by the provider.
 * </p>
 * <p>
 * CSRF protection is off for both chains. There are no cookies and no
 * server-side sessions: every request carries its own credential in the
 * {@code Authorization} header, which a cross-site form cannot set.
 * </p>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(BackOfficeIdentityProperties.class)
public class BackOfficeSecurityConfiguration {

    /**
     * Routes served by POS authentication: the PIN exchange, session lifecycle,
     * orders and payments. All of them authenticate themselves in the service
     * layer, so the filter chain permits them.
     */
    public static final String POS_PATH = "/api/v1/pos/**";

    /**
     * Liveness endpoint. It answers whether the process is up and intentionally
     * reveals nothing that needs a principal.
     */
    public static final String HEALTH_PATH = "/health";

    /**
     * Coarse back-office gate: the realm role an account must carry to use the
     * management API at all. This is not the permission model - M4 adds job
     * roles and a permission catalogue in the database. It exists so an
     * identity provider account that was created for something else cannot use
     * the back office merely by being linked to an employee.
     */
    public static final String BACK_OFFICE_ROLE = "BRAND_ADMIN";

    /**
     * POS and liveness endpoints: no token handling at the filter level.
     * @param http the security builder for this chain
     * @return the POS filter chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    @Order(1)
    public SecurityFilterChain posEndpoints(HttpSecurity http) throws Exception {
        http
                .securityMatcher(POS_PATH, HEALTH_PATH)
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * Every other endpoint: a validated, linked, back-office identity.
     * @param http the security builder for this chain
     * @param converter the JWT to principal converter
     * @param handlers the problem-detail 401/403 writers
     * @return the management filter chain
     * @throws Exception when the chain cannot be built
     */
    @Bean
    @Order(2)
    public SecurityFilterChain backOfficeEndpoints(HttpSecurity http,
                                                   BackOfficeAuthenticationConverter converter,
                                                   BackOfficeSecurityHandlers handlers) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        // The container forwards errors to /error on an ERROR
                        // dispatch. That dispatch has already been authorized
                        // or refused once; requiring a token again here would
                        // replace every rendered 404 with a 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .anyRequest().hasRole(BACK_OFFICE_ROLE))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(handlers)
                        .accessDeniedHandler(handlers));
        return http.build();
    }

    /**
     * Build the token decoder from the configured issuer and JWK set.
     * <p>
     * Timestamp, issuer and audience are all validated. The JWK set URL is used
     * directly instead of discovering it from the issuer, so application startup
     * performs no call to the identity provider and a provider outage cannot
     * stop this application from booting.
     * </p>
     * <p>
     * The validators are passed as a list rather than to the varargs overload:
     * the varargs parameter's type is not reifiable and Spring does not mark it
     * {@code @SafeVarargs}, so calling it would put an unchecked-operations
     * warning in every build for no benefit.
     * </p>
     * @param properties the identity provider configuration
     * @return the decoder used by the management chain
     */
    @Bean
    public JwtDecoder backOfficeJwtDecoder(BackOfficeIdentityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        List<OAuth2TokenValidator<Jwt>> validators = List.of(
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()));
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(validators));
        return decoder;
    }

    /**
     * Provide the token converter to this chain, and only to this chain.
     * <p>
     * It is declared here rather than annotated as a component because
     * {@code Converter} is one of the types {@code @WebMvcTest} pulls into a
     * slice: a scanned converter would drag the identity lookup into every
     * controller test, including the POS ones that must not touch it.
     * </p>
     * @param identityService the identity-to-employee resolver
     * @return the converter for the management chain
     */
    @Bean
    public BackOfficeAuthenticationConverter backOfficeAuthenticationConverter(
            BackOfficeIdentityService identityService) {
        return new BackOfficeAuthenticationConverter(identityService);
    }

    /**
     * Provide the problem-detail 401/403 writers to this chain.
     * @param objectMapper the serializer used to write the problem detail
     * @return the handlers for the management chain
     */
    @Bean
    public BackOfficeSecurityHandlers backOfficeSecurityHandlers(ObjectMapper objectMapper) {
        return new BackOfficeSecurityHandlers(objectMapper);
    }
}
