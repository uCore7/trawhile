package com.trawhile.adapter.inbound.security;

import com.trawhile.port.outbound.persistence.ApiKeyLookupPort.ApiKeyPrincipal;
import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Custom Spring Security {@code Authentication} token produced by
 * {@link ApiKeyAuthenticationFilter} after a successful API-key lookup.
 *
 * <p>The principal is the {@link ApiKeyPrincipal} record from the
 * outbound persistence port. The token is marked authenticated immediately
 * on construction because it is only instantiated after the database lookup
 * confirms the key is valid and unexpired (SR-00-C08.F01).</p>
 *
 * <p>Downstream auth-mode classification in {@link SecurityConfig} uses
 * {@code instanceof ApiKeyAuthentication} to distinguish API-key-mode
 * requests from OIDC-session-mode requests.</p>
 */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final ApiKeyPrincipal principal;

    /**
     * Constructs a fully authenticated token for the given API-key principal.
     * No authorities are granted at this stage; scope-level enforcement is
     * the responsibility of the service layer per the hexagonal architecture
     * rules.
     *
     * @param principal the validated API key principal from the persistence lookup
     */
    public ApiKeyAuthentication(ApiKeyPrincipal principal) {
        this(principal, List.of());
    }

    /**
     * Constructs a fully authenticated token with a specific set of authorities.
     *
     * @param principal   the validated API key principal
     * @param authorities granted authorities (may be empty; scope enforcement is service-layer)
     */
    public ApiKeyAuthentication(ApiKeyPrincipal principal,
                                Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        // Credentials are not retained after authentication; the raw key is never
        // stored in the Security context (SR-08-F01.F01 / AGENTS.md anti-pattern).
        return null;
    }

    @Override
    public ApiKeyPrincipal getPrincipal() {
        return principal;
    }
}
