package com.trawhile.adapter.inbound.security;

import com.trawhile.port.inbound.identity.OidcCallbackPort;
import com.trawhile.port.inbound.identity.OidcCallbackPort.CallbackOutcome;
import com.trawhile.port.inbound.identity.OidcCallbackPort.OidcCallbackCommand;
import com.trawhile.port.inbound.identity.OidcCallbackPort.Rejected;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class OidcCallbackHandler implements AuthenticationSuccessHandler {

    public static final String USER_ID_SESSION_ATTRIBUTE = "trawhile.userId";

    private final OidcCallbackPort oidcCallbackPort;
    private final SavedRequestAwareAuthenticationSuccessHandler successHandler;
    private final SecurityContextLogoutHandler logoutHandler;

    public OidcCallbackHandler(OidcCallbackPort oidcCallbackPort) {
        this.oidcCallbackPort = oidcCallbackPort;
        this.successHandler = new SavedRequestAwareAuthenticationSuccessHandler();
        this.logoutHandler = new SecurityContextLogoutHandler();
        this.logoutHandler.setInvalidateHttpSession(true);
        this.logoutHandler.setClearAuthentication(true);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2AuthenticationToken oauthToken = requireOauthToken(authentication);
        OidcUser oidcUser = requireOidcUser(oauthToken);
        HttpSession existingSession = request.getSession(false);
        Optional<UUID> existingUserId = existingAuthenticatedUserId(existingSession);

        CallbackOutcome outcome = oidcCallbackPort.handle(new OidcCallbackCommand(
                oauthToken.getAuthorizedClientRegistrationId(),
                oidcUser.getSubject(),
                oidcUser.getEmail(),
                existingUserId));

        if (outcome instanceof Rejected rejected) {
            logoutHandler.logout(request, response, authentication);
            SecurityContextHolder.clearContext();
            response.sendRedirect(rejected.redirectUrl());
            return;
        }

        request.getSession(true).setAttribute(USER_ID_SESSION_ATTRIBUTE, outcome.userId());
        successHandler.onAuthenticationSuccess(request, response, authentication);
    }

    private static OAuth2AuthenticationToken requireOauthToken(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
            return oauthToken;
        }
        throw new IllegalArgumentException("Expected OAuth2AuthenticationToken");
    }

    private static OidcUser requireOidcUser(OAuth2AuthenticationToken oauthToken) {
        if (oauthToken.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser;
        }
        throw new IllegalArgumentException("Expected OidcUser principal");
    }

    private static Optional<UUID> existingAuthenticatedUserId(HttpSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object userId = session.getAttribute(USER_ID_SESSION_ATTRIBUTE);
        return userId instanceof UUID uuid ? Optional.of(uuid) : Optional.empty();
    }
}
