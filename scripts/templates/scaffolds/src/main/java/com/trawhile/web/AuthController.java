package com.trawhile.web;

import com.trawhile.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.ArrayList;

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
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountService accountService;
    private final ClientRegistrationRepository clientRegistrationRepository;

    public AuthController(AccountService accountService,
                          ClientRegistrationRepository clientRegistrationRepository) {
        this.accountService = accountService;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @PostMapping("/gdpr-notice")
    public ResponseEntity<Void> acknowledgeGdprNotice() {
        // TODO: read PENDING_GDPR session data; call accountService.completeRegistration(sessionData)
        // Returns 400 if no pending registration data in session (SR-F060.F02)
        return ResponseEntity.noContent().build();
    }

    /** SR-F067.F02 — permit-all; used by login page to render sign-in buttons for active providers. */
    @GetMapping("/providers")
    public List<String> getProviders() {
        var providers = new ArrayList<String>();
        if (clientRegistrationRepository instanceof InMemoryClientRegistrationRepository repo) {
            repo.iterator().forEachRemaining(r -> providers.add(r.getRegistrationId()));
        }
        return providers;
    }
}
