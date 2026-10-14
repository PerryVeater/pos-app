package com.example.posapp.service;

import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Derives the client identifier used to scope POS PIN rate limiting.
 * <p>
 * By default the container-provided {@linkplain HttpServletRequest#getRemoteAddr()
 * remote address} is used. The {@code X-Forwarded-For} header is only
 * consulted when {@link RateLimitProperties#trustForwardedFor()} is
 * explicitly enabled, because that header is client-supplied and trivially
 * spoofed: trusting it unconditionally would let a caller rotate fake IPs to
 * evade the limit. It should be enabled only when the application is known to
 * sit behind a trusted reverse proxy that overwrites the header with the real
 * client address, in which case the left-most entry is taken.
 * </p>
 */
@Component
public class PosClientKeyResolver {

    /** Standard de-facto proxy hop header; comma-separated client-first list. */
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private final RateLimitProperties properties;

    /**
     * Constructor for PosClientKeyResolver.
     * @param properties the rate-limit configuration controlling header trust
     */
    public PosClientKeyResolver(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolve the client identifier for the given request.
     * @param request the incoming HTTP request
     * @return a non-null client key; {@code "unknown"} when nothing usable is
     *         available (so unidentified callers still share one bounded
     *         bucket rather than bypassing the limit)
     */
    public String resolve(HttpServletRequest request) {
        if (properties.trustForwardedFor()) {
            String forwarded = request.getHeader(FORWARDED_FOR_HEADER);
            if (forwarded != null && !forwarded.isBlank()) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null || remote.isBlank() ? "unknown" : remote;
    }
}
