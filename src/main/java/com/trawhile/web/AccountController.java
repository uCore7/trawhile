package com.trawhile.web;

import com.trawhile.service.AccountService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/account — profile, OAuth2 provider links, own authorizations, anonymization,
 * report settings, own MCP token management.
 *
 * Endpoints:
 *   GET    /account                    — SR-043  getProfile
 *   PUT    /account/report-settings    — SR-043b saveReportSettings
 *   GET    /account/authorizations     — SR-046  getOwnAuthorizations
 *   POST   /account/providers          — SR-044  linkProvider
 *   DELETE /account/providers/{p}      — SR-045  unlinkProvider
 *   POST   /account/anonymize          — SR-047  anonymizeAccount
 *   GET    /account/mcp-tokens         — SR-065a listOwnMcpTokens
 *   POST   /account/mcp-tokens         — SR-065  generateMcpToken
 *   DELETE /account/mcp-tokens/{id}    — SR-067  revokeMcpToken
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
