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
 * account creation transaction (SR-057a). Returns privacyNoticeUrl if configured.
 * GET  /auth/providers — returns configured OIDC provider registration IDs (SR-085). Permit-all.
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
        // Returns 400 if no pending registration data in session (SR-057a)
        return ResponseEntity.noContent().build();
    }

    /** SR-085 — permit-all; used by login page to render sign-in buttons for active providers. */
    @GetMapping("/providers")
    public List<String> getProviders() {
        var providers = new ArrayList<String>();
        if (clientRegistrationRepository instanceof InMemoryClientRegistrationRepository repo) {
            repo.iterator().forEachRemaining(r -> providers.add(r.getRegistrationId()));
        }
        return providers;
    }
}
