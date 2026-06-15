package com.trawhile.adapter.inbound.observability;

import com.trawhile.adapter.inbound.security.ApiKeyAuthentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet filter that populates SLF4J MDC correlation identifiers for every inbound
 * HTTP request, implementing SR-00-C16.F01.
 *
 * <p>Keys populated (all camelCase per SR-00-C16.F01):
 * <ul>
 *   <li>{@code requestId} — fresh {@link UUID} per request, always set.</li>
 *   <li>{@code sessionId} — Spring Session ID, set only when an {@link HttpSession}
 *       exists for the request (i.e. the caller carries a valid session cookie).</li>
 *   <li>{@code actorId} — pseudonymous user UUID, set when the actor is known:
 *     <ul>
 *       <li>API-key path: derived from
 *           {@link ApiKeyAuthentication#getPrincipal()}.userId().</li>
 *       <li>OIDC-session path: derived from the {@code "trawhile.userId"} session
 *           attribute (a {@link UUID}), set by the OIDC callback flow
 *           (SR-01-F13.F01).</li>
 *     </ul>
 *   </li>
 * </ul>
 * {@code traceId} and {@code spanId} are owned by Micrometer Tracing
 * auto-configuration and are NOT touched by this filter.</p>
 *
 * <p>This filter is registered on both the application servlet context
 * ({@link MdcFilterRegistration}) and the management servlet context
 * ({@link ManagementMdcFilterConfig}) so that requests to the management port
 * also carry correlation identifiers.</p>
 *
 * <p>IMPORTANT: This filter does NOT emit any log lines. Doing so would risk
 * leaking the {@code actorId} or session ID value. The filter is silent
 * infrastructure; only downstream loggers should emit entries (which will
 * already carry the populated MDC).</p>
 */
public final class MdcFilter extends OncePerRequestFilter {

    static final String MDC_REQUEST_ID = "requestId";
    static final String MDC_SESSION_ID = "sessionId";
    static final String MDC_ACTOR_ID   = "actorId";
    static final String SESSION_ATTR_USER_ID = "trawhile.userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            MDC.put(MDC_REQUEST_ID, UUID.randomUUID().toString());

            HttpSession session = request.getSession(false);
            if (session != null) {
                MDC.put(MDC_SESSION_ID, session.getId());
                Object userId = session.getAttribute(SESSION_ATTR_USER_ID);
                if (userId instanceof UUID uuid) {
                    MDC.put(MDC_ACTOR_ID, uuid.toString());
                }
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof ApiKeyAuthentication apiKey) {
                // API-key path overrides any session-derived actorId (API-key requests
                // are stateless and must not pick up a stale session attribute).
                MDC.put(MDC_ACTOR_ID, apiKey.getPrincipal().userId().toString());
            }

            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
            MDC.remove(MDC_SESSION_ID);
            MDC.remove(MDC_ACTOR_ID);
            // Do NOT touch traceId / spanId — those are owned by Micrometer Tracing.
        }
    }
}
