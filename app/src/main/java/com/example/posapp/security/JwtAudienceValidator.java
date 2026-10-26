package com.example.posapp.security;

import java.util.List;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Validates that an access token was addressed to this API.
 * <p>
 * Signature and issuer checks answer "who minted this" and "is it genuine".
 * They do not answer "was it minted for us": an identity provider issues tokens
 * for every registered client, so a token obtained by a totally different
 * application of the same brand would pass both checks. The audience is what
 * separates the APIs of one provider.
 * </p>
 * <p>
 * The failure is reported as {@code invalid_token} so the resource server
 * answers {@code 401} rather than {@code 403}: the credential is not acceptable
 * here, and the caller gains nothing by knowing whether the signature or the
 * audience was the problem.
 * </p>
 */
public class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    /** OAuth2 error code for a token that cannot be used as presented. */
    private static final String INVALID_TOKEN = "invalid_token";

    private final String requiredAudience;

    /**
     * Constructor for JwtAudienceValidator.
     * @param requiredAudience the audience value every accepted token must carry
     */
    public JwtAudienceValidator(String requiredAudience) {
        this.requiredAudience = requiredAudience;
    }

    /**
     * Check the token's {@code aud} claim.
     * <p>
     * {@code Jwt.getAudience()} returns {@code null} rather than an empty list
     * when the claim is absent - which is the normal shape for a token minted by
     * a client without an audience mapper, not an edge case.
     * </p>
     * @param token the validated token to inspect
     * @return success when the required audience is present, failure otherwise
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        List<String> audience = token.getAudience();
        if (audience != null && audience.contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(INVALID_TOKEN,
                "The token is not addressed to this API.", null));
    }
}
