package com.trawhile.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.trawhile.domain.PendingInvitation;
import com.trawhile.domain.TimeRecord;
import com.trawhile.domain.User;
import com.trawhile.domain.UserProfile;
import com.trawhile.exception.BusinessRuleViolationException;
import com.trawhile.exception.EntityNotFoundException;
import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.McpTokenRepository;
import com.trawhile.repository.NodeAuthorizationRepository;
import com.trawhile.repository.NodeRepository;
import com.trawhile.repository.PendingInvitationRepository;
import com.trawhile.repository.TimeRecordRepository;
import com.trawhile.repository.UserProfileRepository;
import com.trawhile.repository.UserRepository;
import com.trawhile.sse.SseEvent;
import com.trawhile.sse.SseDispatcher;
import com.trawhile.web.dto.AuthLevel;
import com.trawhile.web.dto.CreateInvitation201Response;
import com.trawhile.web.dto.NodePathEntry;
import com.trawhile.web.dto.ResendInvitation200Response;
import com.trawhile.web.dto.UserAuthorization;
import com.trawhile.web.dto.UserSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class UserService {

    private static final String ANONYMISED_PLACEHOLDER = "Anonymised user";

    private final AuthorizationService authorizationService;
    private final AuthorizationQueries authorizationQueries;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final NodeAuthorizationRepository authorizationRepository;
    private final TimeRecordRepository timeRecordRepository;
    private final McpTokenRepository mcpTokenRepository;
    private final PendingInvitationRepository pendingInvitationRepository;
    private final NodeRepository nodeRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SseDispatcher sseDispatcher;

    public UserService(AuthorizationService authorizationService,
                       AuthorizationQueries authorizationQueries,
                       UserRepository userRepository,
                       UserProfileRepository userProfileRepository,
                       NodeAuthorizationRepository authorizationRepository,
                       TimeRecordRepository timeRecordRepository,
                       McpTokenRepository mcpTokenRepository,
                       PendingInvitationRepository pendingInvitationRepository,
                       NodeRepository nodeRepository,
                       JdbcTemplate jdbcTemplate,
                       ObjectMapper objectMapper,
                       SseDispatcher sseDispatcher) {
        this.authorizationService = authorizationService;
        this.authorizationQueries = authorizationQueries;
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.authorizationRepository = authorizationRepository;
        this.timeRecordRepository = timeRecordRepository;
        this.mcpTokenRepository = mcpTokenRepository;
        this.pendingInvitationRepository = pendingInvitationRepository;
        this.nodeRepository = nodeRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.sseDispatcher = sseDispatcher;
    }

    public List<UserSummary> listUsers(UUID actingUserId) {
        requireSystemAdmin(actingUserId);
        return userRepository.findAll().stream()
            .map(this::toUserSummary)
            .sorted(Comparator.comparing(summary -> summary.getName() == null ? "" : summary.getName().toLowerCase(Locale.ROOT)))
            .toList();
    }

    public List<com.trawhile.web.dto.PendingInvitation> listInvitations(UUID actingUserId) {
        requireSystemAdmin(actingUserId);
        return pendingInvitationRepository.findAll().stream()
            .sorted(Comparator.comparing(PendingInvitation::invitedAt))
            .map(this::toPendingInvitationDto)
            .toList();
    }

    public CreateInvitation201Response createInvitation(UUID actingUserId, String email, String baseUrl) {
        requireSystemAdmin(actingUserId);

        String normalizedEmail = normalizeEmail(email);
        if (pendingInvitationRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessRuleViolationException("INVITATION_EXISTS", "A pending invitation already exists for " + normalizedEmail);
        }

        OffsetDateTime now = OffsetDateTime.now();
        UUID userId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();

        userRepository.save(new User(userId, now));
        PendingInvitation invitation = pendingInvitationRepository.save(new PendingInvitation(
            invitationId,
            userId,
            normalizedEmail,
            actingUserId,
            now,
            now.plusDays(90)
        ));

        sseDispatcher.dispatch(userId,
            new SseEvent(SseEvent.EventType.AUTHORIZATION_CHANGE, Map.of("userId", userId)));

        return new CreateInvitation201Response(
            mailtoLink(invitation.email(), baseUrl),
            toPendingInvitationDto(invitation)
        );
    }

    public ResendInvitation200Response resendInvitation(UUID actingUserId, UUID invitationId, String baseUrl) {
        requireSystemAdmin(actingUserId);

        PendingInvitation invitation = pendingInvitationRepository.findById(invitationId)
            .orElseThrow(() -> new EntityNotFoundException("PendingInvitation", invitationId));

        PendingInvitation updated = pendingInvitationRepository.save(new PendingInvitation(
            invitation.id(),
            invitation.userId(),
            invitation.email(),
            invitation.invitedBy(),
            invitation.invitedAt(),
            OffsetDateTime.now().plusDays(90)
        ));

        sseDispatcher.dispatch(updated.userId(),
            new SseEvent(SseEvent.EventType.AUTHORIZATION_CHANGE, Map.of("userId", updated.userId())));

        return new ResendInvitation200Response(
            mailtoLink(updated.email(), baseUrl),
            toPendingInvitationDto(updated)
        );
    }

    public void withdrawInvitation(UUID actingUserId, UUID invitationId) {
        requireSystemAdmin(actingUserId);

        PendingInvitation invitation = pendingInvitationRepository.findById(invitationId)
            .orElseThrow(() -> new EntityNotFoundException("PendingInvitation", invitationId));
        scrubUser(invitation.userId());
    }

    public void expireInvitations() {
        pendingInvitationRepository.findAllByExpiresAtBefore(OffsetDateTime.now())
            .forEach(invitation -> scrubUser(invitation.userId()));
    }

    public void removeUser(UUID actingUserId, UUID targetUserId) {
        requireSystemAdmin(actingUserId);
        if (!userRepository.existsById(targetUserId)) {
            throw new EntityNotFoundException("User", targetUserId);
        }
        scrubUser(targetUserId);
    }

    public List<UserAuthorization> getUserAuthorizations(UUID actingUserId, UUID targetUserId) {
        requireSystemAdmin(actingUserId);
        if (!userRepository.existsById(targetUserId)) {
            throw new EntityNotFoundException("User", targetUserId);
        }

        return authorizationQueries.userAuthorizationsWithPaths(targetUserId).stream()
            .map(row -> {
                UserAuthorization authorization = new UserAuthorization();
                authorization.setNodeId(row.nodeId());
                authorization.setAuthorization(AuthLevel.fromValue(row.authorization()));
                authorization.setNodePath(parseNodePath(row.nodePathJson()));
                return authorization;
            })
            .toList();
    }

    public void scrubUser(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();

        timeRecordRepository.findByUserIdAndEndedAtIsNull(userId)
            .ifPresent(activeRecord -> timeRecordRepository.save(stopActiveRecord(activeRecord, now)));

        authorizationRepository.deleteAll(authorizationRepository.findByUserId(userId));
        pendingInvitationRepository.findByUserId(userId).ifPresent(pendingInvitationRepository::delete);
        userProfileRepository.findByUserId(userId).ifPresent(userProfileRepository::delete);
        mcpTokenRepository.revokeAllByUserId(userId);

        if (!hasRetainedHistory(userId)) {
            userRepository.deleteById(userId);
        }

        sseDispatcher.dispatch(userId,
            new SseEvent(SseEvent.EventType.AUTHORIZATION_CHANGE, Map.of("userId", userId)));
        sseDispatcher.dispatch(userId,
            new SseEvent(SseEvent.EventType.MCP_TOKEN_REVOKED, Map.of("userId", userId)));
    }

    private boolean hasRetainedHistory(UUID userId) {
        Integer timeRecordCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM time_records WHERE user_id = ?",
            Integer.class,
            userId
        );
        Integer requestCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM requests WHERE requester_id = ? OR resolved_by = ?",
            Integer.class,
            userId,
            userId
        );
        return (timeRecordCount != null && timeRecordCount > 0)
            || (requestCount != null && requestCount > 0);
    }

    private TimeRecord stopActiveRecord(TimeRecord activeRecord, OffsetDateTime endedAt) {
        return new TimeRecord(
            activeRecord.id(),
            activeRecord.userId(),
            activeRecord.nodeId(),
            activeRecord.startedAt(),
            endedAt,
            activeRecord.timezone(),
            activeRecord.description(),
            activeRecord.createdAt()
        );
    }

    private UserSummary toUserSummary(User user) {
        UserSummary summary;

        UserProfile profile = userProfileRepository.findByUserId(user.id()).orElse(null);
        if (profile != null) {
            summary = new UserSummary(user.id(), UserSummary.StatusEnum.ACTIVE);
            summary.setName(profile.name());
            return summary;
        }

        PendingInvitation invitation = pendingInvitationRepository.findByUserId(user.id()).orElse(null);
        if (invitation != null) {
            summary = new UserSummary(user.id(), UserSummary.StatusEnum.PENDING);
            summary.setName(invitation.email());
            return summary;
        }

        summary = new UserSummary(user.id(), UserSummary.StatusEnum.ANONYMISED);
        summary.setName(ANONYMISED_PLACEHOLDER);
        return summary;
    }

    private com.trawhile.web.dto.PendingInvitation toPendingInvitationDto(PendingInvitation invitation) {
        com.trawhile.web.dto.PendingInvitation dto = new com.trawhile.web.dto.PendingInvitation();
        dto.setId(invitation.id());
        dto.setUserId(invitation.userId());
        dto.setEmail(invitation.email());
        dto.setInvitedBy(invitation.invitedBy() == null ? null : inviterSummary(invitation.invitedBy()));
        dto.setInvitedAt(invitation.invitedAt());
        dto.setExpiresAt(invitation.expiresAt());
        return dto;
    }

    private UserSummary inviterSummary(UUID inviterId) {
        User user = userRepository.findById(inviterId).orElse(null);
        if (user == null) {
            UserSummary summary = new UserSummary(inviterId, UserSummary.StatusEnum.ANONYMISED);
            summary.setName(ANONYMISED_PLACEHOLDER);
            return summary;
        }
        return toUserSummary(user);
    }

    private List<NodePathEntry> parseNodePath(String nodePathJson) {
        try {
            JsonNode root = objectMapper.readTree(nodePathJson);
            return objectMapper.readerForListOf(NodePathEntry.class).readValue(root);
        } catch (tools.jackson.core.JacksonException ex) {
            throw new IllegalStateException("Failed to parse node path payload", ex);
        }
    }

    private String mailtoLink(String email, String baseUrl) {
        String subject = "Invitation to trawhile";
        String body = """
            You have been invited to trawhile.

            Open %s and sign in with the OIDC provider account linked to this invitation address.
            Invitation address: %s

            Supported providers: Google, Apple, Microsoft, or Keycloak.
            """.formatted(baseUrl, email);
        return "mailto:" + email
            + "?subject=" + urlEncode(subject)
            + "&body=" + urlEncode(body);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void requireSystemAdmin(UUID actingUserId) {
        authorizationService.requireAdmin(actingUserId, rootNodeId());
    }

    private UUID rootNodeId() {
        return nodeRepository.findAll().stream()
            .filter(node -> node.parentId() == null)
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Root node not found"))
            .id();
    }
}
