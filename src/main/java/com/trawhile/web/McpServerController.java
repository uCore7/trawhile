package com.trawhile.web;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.trawhile.config.TrawhileConfig;
import com.trawhile.monitoring.MonitoringMetrics;
import com.trawhile.repository.read.McpReadQueries;
import com.trawhile.service.AuthorizationService;
import com.trawhile.service.McpTokenService;
import com.trawhile.service.ReportService;
import com.trawhile.service.SecurityEventService;
import com.trawhile.service.TrackingService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * MCP JSON-RPC tool endpoint. SR-F053.F02 and SR-F069.F01.
 */
@RestController
public class McpServerController {

    private final ObjectMapper objectMapper;
    private final McpTokenService mcpTokenService;
    private final AuthorizationService authorizationService;
    private final McpReadQueries mcpReadQueries;
    private final TrackingService trackingService;
    private final ReportService reportService;
    private final SecurityEventService securityEventService;
    private final MonitoringMetrics monitoringMetrics;
    private final ZoneId companyZone;

    public McpServerController(ObjectMapper objectMapper,
                               McpTokenService mcpTokenService,
                               AuthorizationService authorizationService,
                               McpReadQueries mcpReadQueries,
                               TrackingService trackingService,
                               ReportService reportService,
                               SecurityEventService securityEventService,
                               MonitoringMetrics monitoringMetrics,
                               TrawhileConfig trawhileConfig) {
        this.objectMapper = objectMapper;
        this.mcpTokenService = mcpTokenService;
        this.authorizationService = authorizationService;
        this.mcpReadQueries = mcpReadQueries;
        this.trackingService = trackingService;
        this.reportService = reportService;
        this.securityEventService = securityEventService;
        this.monitoringMetrics = monitoringMetrics;
        this.companyZone = ZoneId.of(trawhileConfig.getTimezone());
    }

    @PostMapping(value = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> handle(@RequestHeader(value = "Authorization", required = false) String authorizationHeader,
                                                      @RequestBody JsonNode requestBody) {
        JsonNode idNode = requestBody.get("id");
        String method = textValue(requestBody.get("method"));
        if (!"tools/call".equals(method)) {
            return ResponseEntity.badRequest().body(jsonRpcError(idNode, -32601, "Unsupported method"));
        }

        String rawToken = extractBearerToken(authorizationHeader);
        McpTokenService.AuthenticatedToken authenticated = mcpTokenService.authenticate(rawToken);

        JsonNode params = requestBody.path("params");
        String toolName = canonicalToolName(textValue(params.get("name")));
        JsonNode arguments = params.path("arguments");

        securityEventService.log(
            "MCP_TOKEN_USED",
            authenticated.userId(),
            Map.of("tokenId", authenticated.tokenId(), "tool", toolName)
        );
        monitoringMetrics.recordMcpToolInvocation(toolName);

        Object result = switch (toolName) {
            case "get_node_tree" -> getNodeTree(authenticated.userId());
            case "get_time_records" -> getTimeRecords(authenticated.userId(), arguments);
            case "get_tracking_status" -> trackingService.getStatus(authenticated.userId());
            case "get_member_summaries" -> getMemberSummaries(authenticated.userId(), arguments);
            default -> null;
        };

        if (result == null && !"get_tracking_status".equals(toolName)) {
            return ResponseEntity.badRequest().body(jsonRpcError(idNode, -32601, "Unsupported tool"));
        }

        return ResponseEntity.ok(jsonRpcResult(idNode, result));
    }

    private List<Map<String, Object>> getNodeTree(UUID actingUserId) {
        List<McpReadQueries.VisibleNodeRow> rows = mcpReadQueries.findVisibleNodeTree(actingUserId);
        Map<UUID, Map<String, Object>> payloadsById = new LinkedHashMap<>();
        for (McpReadQueries.VisibleNodeRow row : rows) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", row.id());
            payload.put("parentId", row.parentId());
            payload.put("name", row.name());
            payload.put("description", row.description());
            payload.put("isActive", row.isActive());
            payload.put("sortOrder", row.sortOrder());
            payload.put("createdAt", row.createdAt());
            payload.put("deactivatedAt", row.deactivatedAt());
            payload.put("color", row.color());
            payload.put("icon", row.icon());
            payload.put("logoUrl", row.hasLogo() ? "/api/v1/nodes/" + row.id() + "/logo" : null);
            payload.put("children", new ArrayList<Map<String, Object>>());
            payloadsById.put(row.id(), payload);
        }

        List<Map<String, Object>> roots = new ArrayList<>();
        for (McpReadQueries.VisibleNodeRow row : rows) {
            Map<String, Object> payload = payloadsById.get(row.id());
            Map<String, Object> parent = row.parentId() == null ? null : payloadsById.get(row.parentId());
            if (parent == null) {
                roots.add(payload);
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
            children.add(payload);
        }
        return roots;
    }

    private Object getTimeRecords(UUID actingUserId, JsonNode arguments) {
        UUID requestedUserId = uuidArgument(arguments, "user_id", "userId");
        UUID nodeId = uuidArgument(arguments, "node_id", "nodeId");
        LocalDate from = localDateArgument(arguments, "date_from", "dateFrom");
        LocalDate to = localDateArgument(arguments, "date_to", "dateTo");

        if (nodeId != null) {
            authorizationService.requireView(actingUserId, nodeId);
        }

        if (requestedUserId != null && !actingUserId.equals(requestedUserId)) {
            return mcpReadQueries.findVisibleDailyTotalsForOtherUser(actingUserId, requestedUserId, nodeId, from, to)
                .stream()
                .map(row -> {
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("userId", row.userId());
                    payload.put("userName", row.userName());
                    payload.put("date", row.periodStart());
                    payload.put("totalSeconds", row.totalSeconds());
                    return payload;
                })
                .toList();
        }

        return reportService.getReport(
            actingUserId,
            "detailed",
            from,
            to,
            actingUserId,
            nodeId
        ).getDetailed();
    }

    private Object getMemberSummaries(UUID actingUserId, JsonNode arguments) {
        String interval = stringArgument(arguments, "interval");
        if (interval == null || interval.isBlank()) {
            interval = "day";
        }

        LocalDate from = localDateArgument(arguments, "date_from", "dateFrom");
        LocalDate to = localDateArgument(arguments, "date_to", "dateTo");
        if (from == null && to != null) {
            from = to;
        }
        if (to == null && from != null) {
            to = from;
        }
        if (from == null && to == null && requiresRange(interval)) {
            from = LocalDate.now(companyZone);
            to = from;
        }

        UUID nodeId = uuidArgument(arguments, "node_id", "nodeId");
        if (nodeId != null) {
            authorizationService.requireView(actingUserId, nodeId);
        }

        return reportService.getMemberSummaries(
            actingUserId,
            interval.toLowerCase(Locale.ROOT),
            from,
            to,
            nodeId,
            null
        );
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new BadCredentialsException("Invalid MCP token");
        }
        String rawToken = authorizationHeader.substring("Bearer ".length()).trim();
        if (rawToken.isEmpty() || rawToken.contains(" ")) {
            throw new BadCredentialsException("Invalid MCP token");
        }
        return rawToken;
    }

    private String canonicalToolName(String rawToolName) {
        if (rawToolName == null) {
            return "";
        }
        return switch (rawToolName) {
            case "getNodeTree" -> "get_node_tree";
            case "getTimeRecords" -> "get_time_records";
            case "getTrackingStatus" -> "get_tracking_status";
            case "getMemberSummaries" -> "get_member_summaries";
            default -> rawToolName;
        };
    }

    private boolean requiresRange(String interval) {
        String normalized = interval.toLowerCase(Locale.ROOT);
        return normalized.equals("day")
            || normalized.equals("week")
            || normalized.equals("month")
            || normalized.equals("year");
    }

    private String stringArgument(JsonNode arguments, String... keys) {
        JsonNode node = firstArgument(arguments, keys);
        return node == null || node.isNull() ? null : node.asText();
    }

    private UUID uuidArgument(JsonNode arguments, String... keys) {
        String value = stringArgument(arguments, keys);
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private LocalDate localDateArgument(JsonNode arguments, String... keys) {
        String value = stringArgument(arguments, keys);
        return value == null || value.isBlank() ? null : LocalDate.parse(value);
    }

    private JsonNode firstArgument(JsonNode arguments, String... keys) {
        if (arguments == null || arguments.isMissingNode() || arguments.isNull()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = arguments.get(key);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private Map<String, Object> jsonRpcResult(JsonNode idNode, Object result) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", jsonRpcId(idNode));
        response.put("result", result);
        return response;
    }

    private Map<String, Object> jsonRpcError(JsonNode idNode, int code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", jsonRpcId(idNode));
        response.put("error", error);
        return response;
    }

    private Object jsonRpcId(JsonNode idNode) {
        return idNode == null || idNode.isNull() ? null : objectMapper.convertValue(idNode, Object.class);
    }
}
