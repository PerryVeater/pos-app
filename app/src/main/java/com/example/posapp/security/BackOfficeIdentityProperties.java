package com.example.posapp.security;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the back-office identity provider.
 * <p>
 * The issuer and the JWK set URL are configured separately on purpose. Spring
 * Boot's {@code issuer-uri} support resolves the provider's metadata at startup,
 * which would make the application refuse to boot whenever the identity
 * provider is unavailable - including every integration test that has nothing to
 * do with authentication. With an explicit JWK set URL the decoder is built
 * without any network call and fetches signing keys on the first token that
 * needs validating.
 * </p>
 * <p>
 * The issuer is still validated against the token's {@code iss} claim, so a
 * token from another realm or deployment is rejected even if the same key
 * signed it.
 * </p>
 *
 * @param issuer the expected {@code iss} value, e.g.
 *        {@code http://localhost:8180/realms/pos}
 * @param jwkSetUri the provider's JWK set URL used to verify signatures
 * @param audience the audience this API answers to; tokens minted for another
 *        API are refused even when otherwise valid
 */
@Validated
@ConfigurationProperties(prefix = "pos.identity")
public record BackOfficeIdentityProperties(
        @NotBlank String issuer,
        @NotBlank String jwkSetUri,
        @NotBlank String audience) {
}
