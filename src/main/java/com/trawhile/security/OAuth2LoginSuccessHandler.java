package com.trawhile.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Redirects after successful OAuth2 login.
 * If in link mode (LINKING_PROVIDER session attribute was set), redirects to /account.
 * Otherwise redirects to /.
 */
@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String redirectUrl = "/";
        // Link mode is cleared in OAuth2UserService; if session still has it, something is off.
        // Default redirect is always /, Angular router handles the rest.
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}
