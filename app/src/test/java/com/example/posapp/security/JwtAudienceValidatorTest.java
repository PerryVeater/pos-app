package com.example.posapp.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit tests for {@link JwtAudienceValidator}.
 * <p>
 * The integration test proves the validator is wired into the decoder; these
 * cases prove what it decides, including the shapes a real provider can
 * produce: a token addressed elsewhere, and a token with no audience claim at
 * all (Keycloak omits {@code aud} for clients without an audience mapper).
 * </p>
 */
class JwtAudienceValidatorTest {

    private final JwtAudienceValidator validator = new JwtAudienceValidator("pos-api");

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("subject")
                .issuedAt(Instant.parse("2026-10-25T12:00:00Z"))
                .expiresAt(Instant.parse("2026-10-25T12:05:00Z"));
    }

    @Test
    @DisplayName("validate: a token carrying the required audience is accepted")
    void requiredAudienceIsAccepted() {
        OAuth2TokenValidatorResult result =
                validator.validate(token().audience(List.of("pos-api")).build());

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("validate: the required audience is found among several")
    void requiredAudienceAmongManyIsAccepted() {
        OAuth2TokenValidatorResult result =
                validator.validate(token().audience(List.of("account", "pos-api", "other")).build());

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("validate: a token addressed to another API is rejected as invalid_token")
    void otherAudienceIsRejected() {
        OAuth2TokenValidatorResult result =
                validator.validate(token().audience(List.of("another-api")).build());

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
    }

    @Test
    @DisplayName("validate: a token with no audience claim is rejected")
    void missingAudienceIsRejected() {
        OAuth2TokenValidatorResult result = validator.validate(token().build());

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.getErrorCode()).isEqualTo("invalid_token"));
    }
}
