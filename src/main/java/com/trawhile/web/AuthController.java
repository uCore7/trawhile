package com.trawhile.web;

import com.trawhile.service.AccountService;
import com.trawhile.security.TrawhileOidcUserService;
import com.trawhile.web.api.AuthApi;
import com.trawhile.web.dto.AcknowledgeGdprNotice200Response;
import com.trawhile.web.dto.GetProviders200Response;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

/**
 * /api/v1/auth — authentication lifecycle endpoints.
 *
 * POST /auth/logout is handled directly by Spring Security (configured in SecurityConfig).
 * POST /auth/gdpr-notice — reads pending registration data from HTTP session and executes the
 * account creation transaction (SR-F060.F02). Returns privacyNoticeUrl only when configured
 * and the newly registered user has at least one effective node authorization.
 * GET  /auth/providers — returns configured OIDC provider registration IDs (SR-F067.F02). Permit-all.
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController implements AuthApi {

    private final AccountService accountService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public AuthController(AccountService accountService,
                          ClientRegistrationRepository clientRegistrationRepository) {
        this.accountService = accountService;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Override
    public ResponseEntity<AcknowledgeGdprNotice200Response> acknowledgeGdprNotice() {
        HttpSession session = currentSession(false);
        if (session == null) {
            return ResponseEntity.badRequest().build();
        }

        AccountService.SessionData sessionData = AccountService.SessionData
            .fromSessionAttribute(session.getAttribute(TrawhileOidcUserService.PENDING_GDPR_SESSION_KEY))
            .orElse(null);
        if (sessionData == null) {
            return ResponseEntity.badRequest().build();
        }

        return accountService.completeRegistration(sessionData)
            .map(result -> {
                session.removeAttribute(TrawhileOidcUserService.PENDING_GDPR_SESSION_KEY);
                authenticateSession(result.userId(), result.provider(), session);

                AcknowledgeGdprNotice200Response response = new AcknowledgeGdprNotice200Response();
                response.setPrivacyNoticeUrl(result.privacyNoticeUrl());
                return ResponseEntity.ok(response);
            })
            .orElseGet(() -> {
                session.removeAttribute(TrawhileOidcUserService.PENDING_GDPR_SESSION_KEY);
                return ResponseEntity.status(302)
                    .header("Location", "/login?error=not_invited")
                    .build();
            });
    }

    /** SR-F067.F02 — permit-all; used by login page to render sign-in buttons for active providers. */
    @Override
    public ResponseEntity<GetProviders200Response> getProviders() {
        var providers = new ArrayList<GetProviders200Response.ProvidersEnum>();
        if (clientRegistrationRepository instanceof InMemoryClientRegistrationRepository repo) {
            repo.iterator().forEachRemaining(r ->
                providers.add(GetProviders200Response.ProvidersEnum.fromValue(r.getRegistrationId())));
        }
        return ResponseEntity.ok(new GetProviders200Response(providers));
    }

    private void authenticateSession(UUID userId, String provider, HttpSession session) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
            "gdpr-notice-session",
            now,
            now.plusSeconds(300),
            Map.of(IdTokenClaimNames.SUB, userId.toString())
        );
        DefaultOidcUser principal = new DefaultOidcUser(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            idToken,
            IdTokenClaimNames.SUB
        );
        OAuth2AuthenticationToken authentication =
            new OAuth2AuthenticationToken(principal, principal.getAuthorities(), provider);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private HttpSession currentSession(boolean create) {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest().getSession(create);
    }
}
