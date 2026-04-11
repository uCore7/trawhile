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
 * POST /auth/gdpr-notice acknowledges the GDPR notice on first login (SR-057a).
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
        // TODO: get authenticated user from SecurityContext; call accountService.acknowledgeGdprNotice(userId)
        return ResponseEntity.noContent().build();
    }
}
