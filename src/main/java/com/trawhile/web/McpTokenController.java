package com.trawhile.web;

import com.trawhile.service.McpTokenService;
import com.trawhile.web.api.McpApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MCP token management — all operations tagged 'mcp' in the API spec. SR-F053–SR-F057.
 *
 * Endpoints:
 *   GET    /account/mcp-tokens        — SR-F054.F01  listOwnMcpTokens
 *   POST   /account/mcp-tokens        — SR-F053.F01  generateMcpToken
 *   DELETE /account/mcp-tokens/{id}   — SR-F055.F01  revokeMcpToken
 *   GET    /admin/mcp-tokens          — SR-F056.F01  listAllMcpTokens (System Admin; effective admin on root)
 *   DELETE /admin/mcp-tokens/{id}     — SR-F057.F01  adminRevokeMcpToken (System Admin; logs to security_events)
 */
@RestController
@RequestMapping("/api/v1")
public class McpTokenController implements McpApi {

    private final McpTokenService mcpTokenService;

    public McpTokenController(McpTokenService mcpTokenService) {
        this.mcpTokenService = mcpTokenService;
    }

    // TODO: implement all endpoints listed above (require System Admin check for admin endpoints)
}
