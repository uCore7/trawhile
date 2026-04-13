package com.trawhile.web;

import com.trawhile.service.AccountService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/account — profile, OIDC provider links, own authorizations, anonymization,
 * report settings, own MCP token management.
 *
 * Endpoints:
 *   GET    /account                    — SR-F043.F01  getProfile (profile + providers + authorizations + last report settings)
 *   PUT    /account/report-settings    — SR-F066.F01  saveReportSettings
 *   POST   /account/providers          — SR-F044.F01  linkProvider
 *   DELETE /account/providers/{p}      — SR-F045.F01  unlinkProvider
 *   POST   /account/anonymize          — SR-F047.F01  anonymizeAccount
 *   GET    /account/mcp-tokens         — SR-F054.F01  listOwnMcpTokens
 *   POST   /account/mcp-tokens         — SR-F053.F01  generateMcpToken
 *   DELETE /account/mcp-tokens/{id}    — SR-F055.F01  revokeMcpToken
 */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // TODO: implement all endpoints listed above
}
