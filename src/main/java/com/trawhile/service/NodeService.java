package com.trawhile.service;

import com.trawhile.domain.AuthLevel;
import com.trawhile.exception.BusinessRuleViolationException;
import com.trawhile.exception.EntityNotFoundException;
import com.trawhile.exception.InputValidationException;
import com.trawhile.repository.NodeAuthorizationRepository;
import com.trawhile.repository.NodeRepository;
import com.trawhile.repository.UserRepository;
import com.trawhile.sse.SseDispatcher;
import com.trawhile.sse.SseEvent;
import com.trawhile.web.dto.CreateChildNodeRequest;
import com.trawhile.web.dto.MoveNodeRequest;
import com.trawhile.web.dto.Node;
import com.trawhile.web.dto.NodeAuthorization;
import com.trawhile.web.dto.NodePathEntry;
import com.trawhile.web.dto.NodeSummary;
import com.trawhile.web.dto.ReorderChildrenRequest;
import com.trawhile.web.dto.UpdateNodeRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class NodeService {

    private static final String ANONYMISED_PLACEHOLDER = "Anonymised user";
    private static final String LOGO_URL_PREFIX = "/api/v1/nodes/";

    private final NodeRepository nodeRepository;
    private final NodeAuthorizationRepository nodeAuthorizationRepository;
    private final AuthorizationService authorizationService;
    private final SseDispatcher sseDispatcher;
    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;

    public NodeService(NodeRepository nodeRepository,
                       NodeAuthorizationRepository nodeAuthorizationRepository,
                       AuthorizationService authorizationService,
                       SseDispatcher sseDispatcher,
                       JdbcTemplate jdbcTemplate,
                       UserRepository userRepository) {
        this.nodeRepository = nodeRepository;
        this.nodeAuthorizationRepository = nodeAuthorizationRepository;
        this.authorizationService = authorizationService;
        this.sseDispatcher = sseDispatcher;
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Node getRootNode(UUID actingUserId) {
        return toDto(actingUserId, rootNode());
    }

    @Transactional(readOnly = true)
    public Node getNode(UUID actingUserId, UUID nodeId) {
        com.trawhile.domain.Node node = requireNode(nodeId);
        authorizationService.requireView(actingUserId, nodeId);
        return toDto(actingUserId, node);
    }

    @Transactional
    public Node createChild(UUID actingUserId, UUID parentId, CreateChildNodeRequest request) {
        com.trawhile.domain.Node parent = requireNode(parentId);
        authorizationService.requireAdmin(actingUserId, parent.id());

        UUID nodeId = UUID.randomUUID();
        int nextSortOrder = nextChildSortOrder(parent.id());
        nodeRepository.save(new com.trawhile.domain.Node(
            nodeId,
            parent.id(),
            requireNonBlank(request.getName(), "name"),
            request.getDescription(),
            true,
            nextSortOrder,
            null,
            null,
            null,
            null,
            null,
            null
        ));

        com.trawhile.domain.Node created = requireNode(nodeId);
        dispatchNodeChange(created.id());
        return toDto(actingUserId, created);
    }

    @Transactional
    public Node updateNode(UUID actingUserId, UUID nodeId, UpdateNodeRequest request) {
        com.trawhile.domain.Node existing = requireNode(nodeId);
        authorizationService.requireAdmin(actingUserId, existing.id());

        String color = request.getColor() != null ? validateColor(request.getColor()) : existing.color();
        String icon = request.getIcon() != null ? validateIcon(request.getIcon()) : existing.icon();

        com.trawhile.domain.Node updated = nodeRepository.save(new com.trawhile.domain.Node(
            existing.id(),
            existing.parentId(),
            request.getName() != null ? requireNonBlank(request.getName(), "name") : existing.name(),
            request.getDescription() != null ? request.getDescription() : existing.description(),
            existing.isActive(),
            existing.sortOrder(),
            existing.createdAt(),
            existing.deactivatedAt(),
            color,
            icon,
            existing.logo(),
            existing.logoMimeType()
        ));

        dispatchNodeChange(updated.id());
        return toDto(actingUserId, updated);
    }

    @Transactional
    public Node uploadLogo(UUID actingUserId, UUID nodeId, byte[] logoBytes, String mimeType) {
        com.trawhile.domain.Node existing = requireNode(nodeId);
        authorizationService.requireAdmin(actingUserId, existing.id());

        com.trawhile.domain.Node updated = nodeRepository.save(new com.trawhile.domain.Node(
            existing.id(),
            existing.parentId(),
            existing.name(),
            existing.description(),
            existing.isActive(),
            existing.sortOrder(),
            existing.createdAt(),
            existing.deactivatedAt(),
            existing.color(),
            existing.icon(),
            logoBytes,
            mimeType
        ));
        return toDto(actingUserId, updated);
    }

    @Transactional(readOnly = true)
    public com.trawhile.domain.Node getLogo(UUID actingUserId, UUID nodeId) {
        com.trawhile.domain.Node node = requireNode(nodeId);
        authorizationService.requireView(actingUserId, node.id());
        if (node.logo() == null || node.logoMimeType() == null) {
            throw new EntityNotFoundException("Node logo not found: " + nodeId);
        }
        return node;
    }

    @Transactional
    public Node deleteLogo(UUID actingUserId, UUID nodeId) {
        com.trawhile.domain.Node existing = requireNode(nodeId);
        authorizationService.requireAdmin(actingUserId, existing.id());

        com.trawhile.domain.Node updated = nodeRepository.save(new com.trawhile.domain.Node(
            existing.id(),
            existing.parentId(),
            existing.name(),
            existing.description(),
            existing.isActive(),
            existing.sortOrder(),
            existing.createdAt(),
            existing.deactivatedAt(),
            existing.color(),
            existing.icon(),
            null,
            null
        ));
        return toDto(actingUserId, updated);
    }

    @Transactional
    public void reorderChildren(UUID actingUserId, UUID parentId, ReorderChildrenRequest request) {
        com.trawhile.domain.Node parent = requireNode(parentId);
        authorizationService.requireAdmin(actingUserId, parent.id());

        List<com.trawhile.domain.Node> currentChildren = nodeRepository.findByParentIdOrderBySortOrder(parent.id());
        List<UUID> submittedIds = request.getChildIds();
        Set<UUID> currentIds = currentChildren.stream().map(com.trawhile.domain.Node::id).collect(java.util.stream.Collectors.toSet());
        Set<UUID> submittedSet = new HashSet<>(submittedIds);
        if (submittedIds.size() != currentChildren.size()
            || submittedSet.size() != submittedIds.size()
            || !submittedSet.equals(currentIds)) {
            throw new InputValidationException("INVALID_CHILD_ORDER", "Submitted childIds must match the current direct children exactly");
        }

        for (int index = 0; index < submittedIds.size(); index++) {
            UUID childId = submittedIds.get(index);
            com.trawhile.domain.Node child = currentChildren.stream()
                .filter(candidate -> candidate.id().equals(childId))
                .findFirst()
                .orElseThrow(() -> new InputValidationException("INVALID_CHILD_ORDER", "Unknown child node: " + childId));
            nodeRepository.save(withSortOrder(child, index));
        }

        dispatchNodeChange(parent.id());
    }

    @Transactional
    public Node deactivateNode(UUID actingUserId, UUID nodeId) {
        com.trawhile.domain.Node existing = requireNode(nodeId);
        authorizationService.requireAdmin(actingUserId, existing.id());

        Integer activeDescendants = jdbcTemplate.queryForObject(
            """
                WITH RECURSIVE descendants AS (
                  SELECT id FROM nodes WHERE parent_id = ?
                  UNION ALL
                  SELECT n.id
                  FROM nodes n
                  JOIN descendants d ON n.parent_id = d.id
                )
                SELECT COUNT(*)
                FROM nodes
                WHERE id IN (SELECT id FROM descendants)
                AND is_active = TRUE
                """,
            Integer.class,
            existing.id()
        );
        if (activeDescendants != null && activeDescendants > 0) {
            throw new BusinessRuleViolationException("ACTIVE_CHILDREN", "Cannot deactivate a node that still has active descendants");
        }

        com.trawhile.domain.Node updated = nodeRepository.save(new com.trawhile.domain.Node(
            existing.id(),
            existing.parentId(),
            existing.name(),
            existing.description(),
            false,
            existing.sortOrder(),
            existing.createdAt(),
            OffsetDateTime.now(),
            existing.color(),
            existing.icon(),
            existing.logo(),
            existing.logoMimeType()
        ));

        dispatchNodeChange(updated.id());
        return toDto(actingUserId, updated);
    }

    @Transactional
    public Node reactivateNode(UUID actingUserId, UUID nodeId) {
        com.trawhile.domain.Node existing = requireNode(nodeId);
        authorizationService.requireAdmin(actingUserId, existing.id());

        com.trawhile.domain.Node updated = nodeRepository.save(new com.trawhile.domain.Node(
            existing.id(),
            existing.parentId(),
            existing.name(),
            existing.description(),
            true,
            existing.sortOrder(),
            existing.createdAt(),
            null,
            existing.color(),
            existing.icon(),
            existing.logo(),
            existing.logoMimeType()
        ));

        dispatchNodeChange(updated.id());
        return toDto(actingUserId, updated);
    }

    @Transactional
    public Node moveNode(UUID actingUserId, UUID nodeId, MoveNodeRequest request) {
        com.trawhile.domain.Node existing = requireNode(nodeId);
        com.trawhile.domain.Node destinationParent = requireNode(request.getDestinationParentId());

        authorizationService.requireAdmin(actingUserId, existing.id());
        authorizationService.requireAdmin(actingUserId, destinationParent.id());

        Boolean invalidDestination = jdbcTemplate.queryForObject(
            """
                WITH RECURSIVE subtree AS (
                  SELECT id FROM nodes WHERE id = ?
                  UNION ALL
                  SELECT n.id
                  FROM nodes n
                  JOIN subtree s ON n.parent_id = s.id
                )
                SELECT EXISTS (SELECT 1 FROM subtree WHERE id = ?)
                """,
            Boolean.class,
            existing.id(),
            destinationParent.id()
        );
        if (Boolean.TRUE.equals(invalidDestination)) {
            throw new BusinessRuleViolationException("INVALID_MOVE_DESTINATION", "Destination cannot be the node itself or one of its descendants");
        }

        List<UUID> oldViewers = viewersOf(existing.id());
        com.trawhile.domain.Node moved = nodeRepository.save(new com.trawhile.domain.Node(
            existing.id(),
            destinationParent.id(),
            existing.name(),
            existing.description(),
            existing.isActive(),
            nextChildSortOrder(destinationParent.id()),
            existing.createdAt(),
            existing.deactivatedAt(),
            existing.color(),
            existing.icon(),
            existing.logo(),
            existing.logoMimeType()
        ));

        dispatchNodeChangeUnion(moved.id(), oldViewers);
        return toDto(actingUserId, moved);
    }

    @Transactional
    public void grantAuthorization(UUID actingUserId, UUID nodeId, UUID userId, com.trawhile.web.dto.AuthLevel authorization) {
        requireNode(nodeId);
        authorizationService.requireAdmin(actingUserId, nodeId);
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User", userId);
        }

        jdbcTemplate.update(
            """
                INSERT INTO node_authorizations (node_id, user_id, auth_level)
                VALUES (?, ?, CAST(? AS auth_level))
                ON CONFLICT (node_id, user_id)
                DO UPDATE SET auth_level = EXCLUDED.auth_level
                """,
            nodeId,
            userId,
            authorization.getValue()
        );

        sseDispatcher.dispatch(userId,
            new SseEvent(SseEvent.EventType.AUTHORIZATION_CHANGE, java.util.Map.of("userId", userId)));
    }

    @Transactional
    public void revokeAuthorization(UUID actingUserId, UUID nodeId, UUID userId) {
        requireNode(nodeId);
        authorizationService.requireAdmin(actingUserId, nodeId);

        com.trawhile.domain.NodeAuthorization authorization = nodeAuthorizationRepository.findByNodeIdAndUserId(nodeId, userId)
            .orElseThrow(() -> new EntityNotFoundException("Node authorization not found: node=%s user=%s".formatted(nodeId, userId)));

        if (authorization.authorization() == AuthLevel.ADMIN) {
            Integer adminCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM node_authorizations WHERE node_id = ? AND auth_level = 'admin'",
                Integer.class,
                nodeId
            );
            if (adminCount != null && adminCount == 1) {
                throw new BusinessRuleViolationException("LAST_ADMIN", "Cannot revoke the last admin authorization on this node");
            }
        }

        nodeAuthorizationRepository.delete(authorization);
        sseDispatcher.dispatch(userId,
            new SseEvent(SseEvent.EventType.AUTHORIZATION_CHANGE, java.util.Map.of("userId", userId)));
    }

    @Transactional(readOnly = true)
    public List<NodeAuthorization> listAuthorizations(UUID actingUserId, UUID nodeId) {
        requireNode(nodeId);
        authorizationService.requireAdmin(actingUserId, nodeId);

        return jdbcTemplate.query(
            """
                WITH RECURSIVE ancestors AS (
                  SELECT id, parent_id, 0 AS depth
                  FROM nodes
                  WHERE id = ?
                  UNION ALL
                  SELECT n.id, n.parent_id, a.depth + 1
                  FROM nodes n
                  JOIN ancestors a ON n.id = a.parent_id
                )
                SELECT DISTINCT ON (na.user_id)
                       na.user_id,
                       COALESCE(up.name, pi.email, ?) AS user_name,
                       na.auth_level::text AS authorization,
                       na.node_id <> ? AS inherited,
                       CASE WHEN na.node_id <> ? THEN na.node_id ELSE NULL END AS inherited_from_node_id
                FROM ancestors a
                JOIN node_authorizations na ON na.node_id = a.id
                LEFT JOIN user_profile up ON up.user_id = na.user_id
                LEFT JOIN pending_invitations pi ON pi.user_id = na.user_id
                ORDER BY na.user_id, na.auth_level DESC, a.depth ASC
                """,
            (rs, rowNum) -> new NodeAuthorization(
                rs.getObject("user_id", UUID.class),
                rs.getString("user_name"),
                com.trawhile.web.dto.AuthLevel.fromValue(rs.getString("authorization")),
                rs.getBoolean("inherited")
            ).inheritedFromNodeId(rs.getObject("inherited_from_node_id", UUID.class)),
            nodeId,
            ANONYMISED_PLACEHOLDER,
            nodeId,
            nodeId
        );
    }

    private com.trawhile.domain.Node rootNode() {
        UUID rootId = jdbcTemplate.queryForObject(
            "SELECT id FROM nodes WHERE parent_id IS NULL",
            UUID.class
        );
        if (rootId == null) {
            throw new EntityNotFoundException("Root node not found");
        }
        return requireNode(rootId);
    }

    private com.trawhile.domain.Node requireNode(UUID nodeId) {
        return nodeRepository.findById(nodeId)
            .orElseThrow(() -> new EntityNotFoundException("Node", nodeId));
    }

    private Node toDto(UUID actingUserId, com.trawhile.domain.Node node) {
        List<NodeSummary> visibleChildren = nodeRepository.findByParentIdOrderBySortOrder(node.id()).stream()
            .filter(child -> authorizationService.hasView(actingUserId, child.id()))
            .map(child -> toSummary(actingUserId, child))
            .toList();

        Node dto = new Node(
            node.id(),
            node.name(),
            node.isActive(),
            node.sortOrder(),
            node.createdAt(),
            ancestorPath(node),
            visibleChildren
        );
        dto.setParentId(node.parentId());
        dto.setDescription(node.description());
        dto.setDeactivatedAt(node.deactivatedAt());
        dto.setEffectiveAuthorization(toDtoAuth(effectiveAuthorization(actingUserId, node.id())));
        dto.setColor(node.color());
        dto.setIcon(node.icon());
        dto.setLogoUrl(logoUrl(node));
        return dto;
    }

    private NodeSummary toSummary(UUID actingUserId, com.trawhile.domain.Node node) {
        NodeSummary summary = new NodeSummary(
            node.id(),
            node.name(),
            node.isActive(),
            node.sortOrder(),
            hasActiveChildren(node.id())
        );
        summary.setDescription(node.description());
        summary.setEffectiveAuthorization(toDtoAuth(effectiveAuthorization(actingUserId, node.id())));
        summary.setColor(node.color());
        summary.setIcon(node.icon());
        summary.setLogoUrl(logoUrl(node));
        return summary;
    }

    private List<NodePathEntry> ancestorPath(com.trawhile.domain.Node node) {
        List<com.trawhile.domain.Node> path = new ArrayList<>();
        UUID currentId = node.parentId();
        while (currentId != null) {
            com.trawhile.domain.Node current = requireNode(currentId);
            path.add(current);
            currentId = current.parentId();
        }
        path.sort(Comparator.comparingInt(this::depth));
        return path.stream()
            .map(ancestor -> new NodePathEntry(ancestor.id(), ancestor.name()))
            .toList();
    }

    private int depth(com.trawhile.domain.Node node) {
        int depth = 0;
        UUID parentId = node.parentId();
        while (parentId != null) {
            depth++;
            parentId = requireNode(parentId).parentId();
        }
        return depth;
    }

    private boolean hasActiveChildren(UUID nodeId) {
        return !nodeRepository.findByParentIdAndIsActiveOrderBySortOrder(nodeId, true).isEmpty();
    }

    private int nextChildSortOrder(UUID parentId) {
        Integer next = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(sort_order), -1) + 1 FROM nodes WHERE parent_id = ?",
            Integer.class,
            parentId
        );
        return next != null ? next : 0;
    }

    private com.trawhile.domain.Node withSortOrder(com.trawhile.domain.Node node, int sortOrder) {
        return new com.trawhile.domain.Node(
            node.id(),
            node.parentId(),
            node.name(),
            node.description(),
            node.isActive(),
            sortOrder,
            node.createdAt(),
            node.deactivatedAt(),
            node.color(),
            node.icon(),
            node.logo(),
            node.logoMimeType()
        );
    }

    private com.trawhile.web.dto.AuthLevel toDtoAuth(AuthLevel authorization) {
        return authorization == null ? null : com.trawhile.web.dto.AuthLevel.fromValue(authorization.name().toLowerCase(Locale.ROOT));
    }

    private AuthLevel effectiveAuthorization(UUID actingUserId, UUID nodeId) {
        return jdbcTemplate.queryForObject(
            """
                WITH RECURSIVE ancestors AS (
                  SELECT id, parent_id FROM nodes WHERE id = ?
                  UNION ALL
                  SELECT n.id, n.parent_id
                  FROM nodes n
                  JOIN ancestors a ON n.id = a.parent_id
                )
                SELECT MAX(na.auth_level)::text
                FROM ancestors a
                JOIN node_authorizations na ON na.node_id = a.id
                WHERE na.user_id = ?
                """,
            (rs, rowNum) -> {
                String value = rs.getString(1);
                return value == null ? null : AuthLevel.valueOf(value.toUpperCase(Locale.ROOT));
            },
            nodeId,
            actingUserId
        );
    }

    private String logoUrl(com.trawhile.domain.Node node) {
        return node.logo() == null ? null : LOGO_URL_PREFIX + node.id() + "/logo";
    }

    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InputValidationException("INVALID_" + fieldName.toUpperCase(Locale.ROOT), fieldName + " must not be blank");
        }
        return value;
    }

    private String validateColor(String color) {
        if (!color.matches("^#[0-9A-Fa-f]{6}$")) {
            throw new InputValidationException("INVALID_COLOR", "Color must be a CSS hex value like #4A90D9");
        }
        return color;
    }

    private String validateIcon(String icon) {
        if (!icon.matches("^pi-[A-Za-z0-9-]+$")) {
            throw new InputValidationException("INVALID_ICON", "Icon must be a PrimeIcons identifier like pi-briefcase");
        }
        return icon;
    }

    private List<UUID> viewersOf(UUID nodeId) {
        return jdbcTemplate.queryForList(
            """
                WITH RECURSIVE ancestors AS (
                  SELECT id, parent_id FROM nodes WHERE id = ?
                  UNION ALL
                  SELECT n.id, n.parent_id
                  FROM nodes n
                  JOIN ancestors a ON n.id = a.parent_id
                )
                SELECT na.user_id
                FROM ancestors a
                JOIN node_authorizations na ON na.node_id = a.id
                GROUP BY na.user_id
                HAVING MAX(na.auth_level) >= CAST('view' AS auth_level)
                """,
            UUID.class,
            nodeId
        );
    }

    private void dispatchNodeChange(UUID nodeId) {
        List<UUID> viewers = viewersOf(nodeId);
        sseDispatcher.dispatchToAll(viewers,
            new SseEvent(SseEvent.EventType.NODE_CHANGE, java.util.Map.of("nodeId", nodeId)));
    }

    private void dispatchNodeChangeUnion(UUID nodeId, List<UUID> priorViewers) {
        Set<UUID> recipients = new HashSet<>(priorViewers);
        recipients.addAll(viewersOf(nodeId));
        sseDispatcher.dispatchToAll(List.copyOf(recipients),
            new SseEvent(SseEvent.EventType.NODE_CHANGE, java.util.Map.of("nodeId", nodeId)));
    }
}
