package com.trawhile.web;

import com.trawhile.service.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/auth — authentication lifecycle endpoints.
 *
 * POST /auth/logout is handled directly by Spring Security (configured in SecurityConfig).
 * POST /auth/gdpr-notice — reads pending registration data from HTTP session and executes the
 * account creation transaction (SR-057a). Returns privacyNoticeUrl if configured.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AccountService accountService;

    public AuthController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/gdpr-notice")
    public ResponseEntity<Void> acknowledgeGdprNotice() {
        // TODO: read pending registration data from HttpSession; call accountService.completeRegistration(sessionData)
        // Returns 400 if no pending registration data in session (SR-057a)
        return ResponseEntity.noContent().build();
    }
}
