package com.trawhile.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Redirects after successful OAuth2 login.
 *
 * Three outcomes, determined by session attributes set in TrawhileOidcUserService.loadUser:
 *
 *   LINK_COMPLETE = true   → provider-linking flow; redirect to /account
 *   PENDING_GDPR  = (data) → new user, pending GDPR acknowledgement; redirect to /gdpr-notice
 *   (neither)              → returning user; redirect to /
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        HttpSession session = request.getSession(false);

        if (session != null && Boolean.TRUE.equals(session.getAttribute("LINK_COMPLETE"))) {
            session.removeAttribute("LINK_COMPLETE");
            getRedirectStrategy().sendRedirect(request, response, "/account");
            return;
        }

        if (session != null && session.getAttribute("PENDING_GDPR") != null) {
            // Session data stored by TrawhileOidcUserService; consumed by POST /api/v1/auth/gdpr-notice
            getRedirectStrategy().sendRedirect(request, response, "/gdpr-notice");
            return;
        }

        getRedirectStrategy().sendRedirect(request, response, "/");
    }
}
