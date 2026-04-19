package com.trawhile.web;

import com.trawhile.service.AccountService;
import com.trawhile.web.api.AccountApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    // TODO: implement all endpoints listed above
}
