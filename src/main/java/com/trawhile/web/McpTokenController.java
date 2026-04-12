package com.trawhile.web;

import com.trawhile.service.McpTokenService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /api/v1/admin/mcp-tokens — System Admin view and revocation of all MCP tokens. SR-F056.F01/SR-F057.F01.
 *
 * Own-token endpoints (GET/POST/DELETE /api/v1/account/mcp-tokens) are in AccountController.
 *
 * Endpoints:
 *   GET    /admin/mcp-tokens          — SR-F056.F01  listAllMcpTokens (System Admin; effective admin on root)
 *   DELETE /admin/mcp-tokens/{id}     — SR-F057.F01  adminRevokeMcpToken (System Admin; logs to security_events)
 */
@RestController
@RequestMapping("/api/v1/admin/mcp-tokens")
public class McpTokenController {

    private final McpTokenService mcpTokenService;

    public McpTokenController(McpTokenService mcpTokenService) {
        this.mcpTokenService = mcpTokenService;
    }

    // TODO: implement all endpoints listed above (require System Admin check for admin endpoints)
}
