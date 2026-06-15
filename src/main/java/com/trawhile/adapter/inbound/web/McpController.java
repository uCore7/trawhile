package com.trawhile.adapter.inbound.web;

import com.trawhile.adapter.inbound.web.api.McpApi;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registers the generated MCP transport routes with Spring MVC.
 *
 * <p>The actual MCP server implementation is not wired in this phase; the
 * generated default methods therefore still return 501 for well-formed
 * requests. Registering the route ensures malformed JSON reaches the MVC
 * request-body parser, where {@link MalformedRequestBodyLogger} handles it
 * without logging raw payload content.</p>
 */
@RestController
public class McpController implements McpApi {
}
