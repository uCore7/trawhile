package com.trawhile.web;

import com.trawhile.security.TrawhileOidcUserService;
import com.trawhile.service.AccountService;
import com.trawhile.web.api.AccountApi;
import com.trawhile.web.dto.GetProfile200Response;
import com.trawhile.web.dto.LinkProviderRequest;
import com.trawhile.web.dto.UserAuthorization;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /api/v1/account — profile, OIDC provider links, own authorizations, anonymization,
 * report settings.
 *
 * Endpoints:
 *   GET    /account                    — SR-F043.F01  getProfile (profile + providers + authorizations + last report settings)
 *   PUT    /account/report-settings    — SR-F066.F01  saveReportSettings
 *   GET    /account/authorizations     — SR-F043.F01  getOwnAuthorizations
 *   POST   /account/providers          — SR-F044.F01  linkProvider
 *   DELETE /account/providers/{p}      — SR-F045.F01  unlinkProvider
 *   POST   /account/anonymize          — SR-F047.F01  anonymizeAccount
 *
 * MCP token endpoints (/account/mcp-tokens and /admin/mcp-tokens) are in McpTokenController.
 */
@RestController
@RequestMapping("/api/v1")
public class AccountController implements AccountApi {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public ResponseEntity<GetProfile200Response> getProfile() {
        return ResponseEntity.ok(accountService.getProfile(currentUserId()));
    }

    @Override
    public ResponseEntity<Void> saveReportSettings(Map<String, Object> requestBody) {
        accountService.saveReportSettings(currentUserId(), requestBody);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<UserAuthorization>> getOwnAuthorizations() {
        return ResponseEntity.ok(accountService.getOwnAuthorizations(currentUserId()));
    }

    @Override
    public ResponseEntity<Void> linkProvider(LinkProviderRequest linkProviderRequest) {
        String provider = linkProviderRequest.getProvider().getValue();
        currentSession(true).setAttribute(TrawhileOidcUserService.LINKING_PROVIDER_SESSION_KEY, provider);
        accountService.linkProvider(currentUserId(), provider, currentUserId().toString());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unlinkProvider(String provider) {
        accountService.unlinkProvider(currentUserId(), provider);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> anonymizeAccount() {
        accountService.anonymizeAccount(currentUserId());

        HttpSession session = currentSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private HttpSession currentSession(boolean create) {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest().getSession(create);
    }
}
