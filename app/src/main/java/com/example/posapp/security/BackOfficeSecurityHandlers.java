package com.example.posapp.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders back-office authentication and authorization failures as RFC 9457
 * problem details.
 * <p>
 * These two failures happen inside the servlet filter chain, before any handler
 * is selected, so {@link com.example.posapp.exception.ApiExceptionHandler} never
 * sees them. Without an explicit handler Spring Security writes an empty body
 * with a {@code WWW-Authenticate} header, which is a different error shape from
 * every other 4xx in this API.
 * </p>
 * <p>
 * The bodies are fixed strings. A missing token, an expired token, a token for
 * another audience and one signed by an unknown key are all the same 401, and a
 * subject with no employee is the same 403 as a subject that has one but lacks
 * the role. The distinction exists in the logs; it does not exist on the wire.
 * </p>
 */
public class BackOfficeSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    /**
     * Constructor for BackOfficeSecurityHandlers.
     * @param objectMapper the serializer used to write the problem detail
     */
    public BackOfficeSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Answer an unauthenticated request with 401 Unauthorized.
     * @param request the rejected request
     * @param response the response to write
     * @param authException the reason authentication failed
     * @throws IOException when the response cannot be written
     */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        // RFC 6750 requires a challenge; the parameter list is empty so the
        // response does not say whether a token was missing, malformed or
        // merely unacceptable.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpStatus.UNAUTHORIZED, "Authentication required",
                "A valid back-office access token is required for this endpoint.");
    }

    /**
     * Answer an authenticated but unusable identity with 403 Forbidden.
     * @param request the rejected request
     * @param response the response to write
     * @param accessDeniedException the reason access was refused
     * @throws IOException when the response cannot be written
     */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "Access denied",
                "This identity is not permitted to use the back-office API.");
    }

    /**
     * Serialize a problem detail into the response.
     * @param response the response to write
     * @param status the status code and problem status
     * @param title a short, human-readable summary
     * @param detail a human-readable explanation
     * @throws IOException when the response cannot be written
     */
    private void write(HttpServletResponse response, HttpStatus status, String title,
                       String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
