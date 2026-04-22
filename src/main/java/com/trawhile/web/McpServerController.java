package com.trawhile.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trawhile.config.TrawhileConfig;
import com.trawhile.domain.TimeRecord;
import com.trawhile.monitoring.MonitoringMetrics;
import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.NodeRepository;
import com.trawhile.repository.TimeRecordRepository;
import com.trawhile.repository.UserProfileRepository;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MCP JSON-RPC tool endpoint. SR-F053.F02 and SR-F069.F01.
 */
@RestController
public class McpServerController {

    private final ObjectMapper objectMapper;
    private final McpTokenService mcpTokenService;
    private final AuthorizationService authorizationService;
    private final AuthorizationQueries authorizationQueries;
    private final NodeRepository nodeRepository;
    private final TimeRecordRepository timeRecordRepository;
    private final UserProfileRepository userProfileRepository;
    private final TrackingService trackingService;
    private final ReportService reportService;
    private final SecurityEventService securityEventService;
    private final MonitoringMetrics monitoringMetrics;
    private final ZoneId companyZone;

    public McpServerController(ObjectMapper objectMapper,
                               McpTokenService mcpTokenService,
                               AuthorizationService authorizationService,
                               AuthorizationQueries authorizationQueries,
                               NodeRepository nodeRepository,
                               TimeRecordRepository timeRecordRepository,
                               UserProfileRepository userProfileRepository,
                               TrackingService trackingService,
                               ReportService reportService,
                               SecurityEventService securityEventService,
                               MonitoringMetrics monitoringMetrics,
                               TrawhileConfig trawhileConfig) {
        this.objectMapper = objectMapper;
        this.mcpTokenService = mcpTokenService;
        this.authorizationService = authorizationService;
        this.authorizationQueries = authorizationQueries;
        this.nodeRepository = nodeRepository;
        this.timeRecordRepository = timeRecordRepository;
        this.userProfileRepository = userProfileRepository;
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
        Map<UUID, com.trawhile.domain.Node> nodesById = nodeRepository.findAll().stream()
            .collect(Collectors.toMap(com.trawhile.domain.Node::id, node -> node));
        Set<UUID> visibleNodeIds = new LinkedHashSet<>(authorizationQueries.visibleNodeIds(actingUserId));
        Map<UUID, List<com.trawhile.domain.Node>> childrenByParentId = nodeRepository.findAll().stream()
            .collect(Collectors.groupingBy(
                com.trawhile.domain.Node::parentId,
                Collectors.collectingAndThen(Collectors.toList(), children -> children.stream()
                    .sorted(Comparator.comparing(com.trawhile.domain.Node::sortOrder).thenComparing(com.trawhile.domain.Node::id))
                    .toList())
            ));

        return visibleNodeIds.stream()
            .map(nodesById::get)
            .filter(Objects::nonNull)
            .filter(node -> node.parentId() == null || !visibleNodeIds.contains(node.parentId()))
            .sorted(Comparator.comparing(com.trawhile.domain.Node::sortOrder).thenComparing(com.trawhile.domain.Node::id))
            .map(node -> toVisibleNode(node, visibleNodeIds, childrenByParentId))
            .toList();
    }

    private Object getTimeRecords(UUID actingUserId, JsonNode arguments) {
        UUID requestedUserId = uuidArgument(arguments, "user_id", "userId");
        UUID effectiveUserId = requestedUserId == null ? actingUserId : requestedUserId;
        UUID nodeId = uuidArgument(arguments, "node_id", "nodeId");
        LocalDate from = localDateArgument(arguments, "date_from", "dateFrom");
        LocalDate to = localDateArgument(arguments, "date_to", "dateTo");

        if (!actingUserId.equals(effectiveUserId)) {
            return aggregateDailyTotals(actingUserId, effectiveUserId, nodeId, from, to);
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

        return reportService.getMemberSummaries(
            actingUserId,
            interval.toLowerCase(Locale.ROOT),
            from,
            to,
            uuidArgument(arguments, "node_id", "nodeId"),
            null
        );
    }

    private List<Map<String, Object>> aggregateDailyTotals(UUID actingUserId,
                                                           UUID targetUserId,
                                                           UUID nodeId,
                                                           LocalDate from,
                                                           LocalDate to) {
        Set<UUID> visibleNodeIds = new HashSet<>(authorizationQueries.visibleNodeIds(actingUserId));
        if (nodeId != null) {
            authorizationService.requireView(actingUserId, nodeId);
        }

        Set<UUID> allowedNodeIds = resolveAllowedNodeIds(visibleNodeIds, nodeId);
        List<TimeRecord> records = timeRecordRepository.findAll().stream()
            .filter(record -> targetUserId.equals(record.userId()))
            .filter(record -> allowedNodeIds.contains(record.nodeId()))
            .sorted(Comparator.comparing(TimeRecord::startedAt).thenComparing(TimeRecord::id))
            .toList();

        if (records.isEmpty()) {
            return List.of();
        }

        LocalDate effectiveFrom = from != null ? from : records.stream()
            .map(record -> record.startedAt().atZoneSameInstant(companyZone).toLocalDate())
            .min(LocalDate::compareTo)
            .orElse(null);
        LocalDate effectiveTo = to != null ? to : records.stream()
            .map(record -> effectiveEndedAt(record).atZoneSameInstant(companyZone).toLocalDate())
            .max(LocalDate::compareTo)
            .orElse(null);

        if (effectiveFrom == null || effectiveTo == null || effectiveTo.isBefore(effectiveFrom)) {
            return List.of();
        }

        String userName = userProfileRepository.findByUserId(targetUserId)
            .map(com.trawhile.domain.UserProfile::name)
            .orElse(null);

        List<Map<String, Object>> totals = new ArrayList<>();
        for (LocalDate date = effectiveFrom; !date.isAfter(effectiveTo); date = date.plusDays(1)) {
            ZonedDateTime bucketStart = date.atStartOfDay(companyZone);
            ZonedDateTime bucketEnd = date.plusDays(1).atStartOfDay(companyZone);

            long totalSeconds = 0L;
            for (TimeRecord record : records) {
                totalSeconds += overlapSeconds(record, bucketStart, bucketEnd);
            }
            if (totalSeconds == 0L) {
                continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", targetUserId);
            row.put("userName", userName);
            row.put("date", date);
            row.put("totalSeconds", totalSeconds);
            totals.add(row);
        }
        return totals;
    }

    private Map<String, Object> toVisibleNode(com.trawhile.domain.Node node,
                                              Set<UUID> visibleNodeIds,
                                              Map<UUID, List<com.trawhile.domain.Node>> childrenByParentId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", node.id());
        payload.put("parentId", node.parentId());
        payload.put("name", node.name());
        payload.put("description", node.description());
        payload.put("isActive", node.isActive());
        payload.put("sortOrder", node.sortOrder());
        payload.put("createdAt", node.createdAt());
        payload.put("deactivatedAt", node.deactivatedAt());
        payload.put("color", node.color());
        payload.put("icon", node.icon());
        payload.put("logoUrl", node.logo() == null ? null : "/api/v1/nodes/" + node.id() + "/logo");
        payload.put(
            "children",
            childrenByParentId.getOrDefault(node.id(), List.of()).stream()
                .filter(child -> visibleNodeIds.contains(child.id()))
                .map(child -> toVisibleNode(child, visibleNodeIds, childrenByParentId))
                .toList()
        );
        return payload;
    }

    private Set<UUID> resolveAllowedNodeIds(Set<UUID> visibleNodeIds, UUID nodeId) {
        if (nodeId == null) {
            return visibleNodeIds;
        }

        Map<UUID, List<UUID>> childrenByParentId = nodeRepository.findAll().stream()
            .collect(Collectors.groupingBy(
                com.trawhile.domain.Node::parentId,
                Collectors.mapping(com.trawhile.domain.Node::id, Collectors.toList())
            ));

        Set<UUID> subtreeNodeIds = new HashSet<>();
        ArrayDeque<UUID> queue = new ArrayDeque<>();
        queue.add(nodeId);
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            if (!subtreeNodeIds.add(current)) {
                continue;
            }
            childrenByParentId.getOrDefault(current, List.of()).forEach(queue::addLast);
        }
        subtreeNodeIds.retainAll(visibleNodeIds);
        return subtreeNodeIds;
    }

    private long overlapSeconds(TimeRecord record, ZonedDateTime bucketStart, ZonedDateTime bucketEnd) {
        ZonedDateTime recordStart = record.startedAt().atZoneSameInstant(companyZone);
        ZonedDateTime recordEnd = effectiveEndedAt(record).atZoneSameInstant(companyZone);
        ZonedDateTime overlapStart = recordStart.isAfter(bucketStart) ? recordStart : bucketStart;
        ZonedDateTime overlapEnd = recordEnd.isBefore(bucketEnd) ? recordEnd : bucketEnd;
        if (!overlapStart.isBefore(overlapEnd)) {
            return 0L;
        }
        return Duration.between(overlapStart, overlapEnd).toSeconds();
    }

    private OffsetDateTime effectiveEndedAt(TimeRecord record) {
        return record.endedAt() != null ? record.endedAt() : OffsetDateTime.now();
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
