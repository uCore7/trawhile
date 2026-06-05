package com.trawhile.adapter.inbound.security;

import com.trawhile.port.outbound.persistence.ApiKeyLookupPort;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spring Security filter that extracts and validates API-key bearer tokens.
 *
 * <p>For each request:
 * <ol>
 *   <li>If the {@code Authorization} header is absent or does not start with
 *       {@code Bearer } (case-insensitive), the filter passes through without
 *       touching the {@link SecurityContextHolder}. The OIDC session filter
 *       further down the chain handles session-cookie requests.</li>
 *   <li>If the header starts with {@code Bearer }, the raw key after the prefix
 *       is SHA-256 hashed and looked up via {@link ApiKeyLookupPort}.</li>
 *   <li>If the lookup returns empty (revoked, expired, or unknown key), the
 *       filter responds with HTTP 401 and does not call
 *       {@code filterChain.doFilter()}.</li>
 *   <li>If the lookup succeeds, an {@link ApiKeyAuthentication} is placed in the
 *       {@link SecurityContextHolder} and the chain continues.</li>
 * </ol>
 * </p>
 *
 * <p>The raw API key and the {@code Authorization} header value are never logged
 * (AGENTS.md: "Do not log raw API keys"; SR-00-C14.F01 / UR-00-C14 PII policy).
 * Only the derived SHA-256 hash is used for the persistence lookup, and even
 * that is not logged at any level.</p>
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "bearer ";

    private final ApiKeyLookupPort apiKeyLookupPort;

    public ApiKeyAuthenticationFilter(ApiKeyLookupPort apiKeyLookupPort) {
        this.apiKeyLookupPort = apiKeyLookupPort;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");
        if (authorization == null
                || !authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            // No Bearer token — let the chain proceed; session-cookie auth handles
            // the OIDC path further down.
            filterChain.doFilter(request, response);
            return;
        }

        // Extract raw key without logging it (AGENTS.md anti-pattern).
        String rawKey = authorization.substring(BEARER_PREFIX.length());
        String hash = sha256Hex(rawKey);

        Optional<ApiKeyLookupPort.ApiKeyPrincipal> principal =
                apiKeyLookupPort.findValidByHash(hash);

        if (principal.isEmpty()) {
            // Key is absent, revoked, or expired — reject with 401 per SR-08-F03.C01.
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        ApiKeyAuthentication authentication = new ApiKeyAuthentication(principal.get());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    /**
     * Computes the SHA-256 digest of {@code input} as a 64-character lowercase
     * hex string. This is the hash format stored in {@code api_keys.key_hash}
     * (spec/schema.sql: {@code VARCHAR(64) NOT NULL}).
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
