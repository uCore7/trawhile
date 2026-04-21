package com.trawhile.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trawhile.config.TrawhileConfig;
import com.trawhile.domain.NodeAuthorization;
import com.trawhile.domain.User;
import com.trawhile.domain.UserOauthProvider;
import com.trawhile.domain.UserProfile;
import com.trawhile.exception.BusinessRuleViolationException;
import com.trawhile.exception.EntityNotFoundException;
import com.trawhile.repository.*;
import com.trawhile.sse.SseDispatcher;
import com.trawhile.sse.SseEvent;
import com.trawhile.web.dto.AuthLevel;
import com.trawhile.web.dto.GetProfile200Response;
import com.trawhile.web.dto.NodePathEntry;
import com.trawhile.web.dto.UserAuthorization;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    public record SessionData(
        UUID invitationId,
        UUID userId,
        String provider,
        String subject,
        String name,
        boolean bootstrap
    ) {
        public static Optional<SessionData> fromSessionAttribute(Object attribute) {
            if (!(attribute instanceof Map<?, ?> values)) {
                return Optional.empty();
            }
            try {
                UUID invitationId = values.get("invitationId") == null
                    ? null
                    : UUID.fromString(String.valueOf(values.get("invitationId")));
                UUID userId = UUID.fromString(String.valueOf(values.get("userId")));
                String provider = String.valueOf(values.get("provider"));
                String subject = String.valueOf(values.get("subject"));
                String name = String.valueOf(values.get("name"));
                Object bootstrapValue = values.containsKey("bootstrap") ? values.get("bootstrap") : false;
                boolean bootstrap = Boolean.parseBoolean(String.valueOf(bootstrapValue));
                return Optional.of(new SessionData(invitationId, userId, provider, subject, name, bootstrap));
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        }
    }

    public record RegistrationResult(UUID userId, String provider, URI privacyNoticeUrl) {
    }

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserOauthProviderRepository oauthProviderRepository;
    private final NodeAuthorizationRepository authorizationRepository;
    private final McpTokenRepository mcpTokenRepository;
    private final PendingInvitationRepository pendingInvitationRepository;
    private final AuthorizationQueries authorizationQueries;
    private final AuthorizationService authorizationService;
    private final NodeRepository nodeRepository;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final TrawhileConfig trawhileConfig;
    private final SseDispatcher sseDispatcher;

    public AccountService(UserRepository userRepository,
                          UserProfileRepository userProfileRepository,
                          UserOauthProviderRepository oauthProviderRepository,
                          NodeAuthorizationRepository authorizationRepository,
                          McpTokenRepository mcpTokenRepository,
                          PendingInvitationRepository pendingInvitationRepository,
                          AuthorizationQueries authorizationQueries,
                          AuthorizationService authorizationService,
                          NodeRepository nodeRepository,
                          UserService userService,
                          ObjectMapper objectMapper,
                          TrawhileConfig trawhileConfig,
                          SseDispatcher sseDispatcher) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.oauthProviderRepository = oauthProviderRepository;
        this.authorizationRepository = authorizationRepository;
        this.mcpTokenRepository = mcpTokenRepository;
        this.pendingInvitationRepository = pendingInvitationRepository;
        this.authorizationQueries = authorizationQueries;
        this.authorizationService = authorizationService;
        this.nodeRepository = nodeRepository;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.trawhileConfig = trawhileConfig;
        this.sseDispatcher = sseDispatcher;
    }

    @Transactional(readOnly = true)
    public GetProfile200Response getProfile(UUID userId) {
        UserProfile profile = userProfile(userId);

        GetProfile200Response response = new GetProfile200Response(
            userId,
            profile.name(),
            oauthProviderRepository.findByProfileId(profile.id()).stream()
                .map(UserOauthProvider::provider)
                .sorted()
                .map(GetProfile200Response.ProvidersEnum::fromValue)
                .toList()
        );
        response.setLastReportSettings(parseJsonMap(profile.lastReportSettings()));
        return response;
    }

    @Transactional
    public void saveReportSettings(UUID userId, Map<String, Object> reportSettings) {
        UserProfile profile = userProfile(userId);
        userProfileRepository.save(new UserProfile(
            profile.id(),
            profile.userId(),
            profile.name(),
            writeJson(reportSettings)
        ));
    }

    @Transactional
    public void linkProvider(UUID userId, String provider, String subject) {
        UserProfile profile = userProfile(userId);

        oauthProviderRepository.findByProviderAndSubject(provider, subject)
            .ifPresent(existing -> {
                if (!existing.profileId().equals(profile.id())) {
                    throw new BusinessRuleViolationException(
                        "PROVIDER_ALREADY_LINKED",
                        "This provider account is already linked to another user"
                    );
                }
            });

        UserOauthProvider existingProvider = oauthProviderRepository.findByProfileId(profile.id()).stream()
            .filter(link -> link.provider().equals(provider))
            .findFirst()
            .orElse(null);

        if (existingProvider == null) {
            oauthProviderRepository.save(new UserOauthProvider(
                UUID.randomUUID(),
                profile.id(),
                provider,
                subject
            ));
        } else if (!existingProvider.subject().equals(subject)) {
            oauthProviderRepository.save(new UserOauthProvider(
                existingProvider.id(),
                existingProvider.profileId(),
                existingProvider.provider(),
                subject
            ));
        }
    }

    @Transactional
    public void unlinkProvider(UUID userId, String provider) {
        UserProfile profile = userProfile(userId);
        List<UserOauthProvider> providers = oauthProviderRepository.findByProfileId(profile.id());
        List<UserOauthProvider> matches = providers.stream()
            .filter(link -> link.provider().equals(provider))
            .toList();

        if (matches.isEmpty()) {
            return;
        }
        if (providers.size() <= 1) {
            throw new BusinessRuleViolationException(
                "LAST_PROVIDER",
                "Cannot unlink the last remaining provider"
            );
        }

        oauthProviderRepository.deleteAll(matches);
    }

    @Transactional(readOnly = true)
    public List<UserAuthorization> getOwnAuthorizations(UUID userId) {
        userProfile(userId);
        return authorizationQueries.userAuthorizationsWithPaths(userId).stream()
            .sorted(Comparator.comparing(AuthorizationQueries.UserAuthorizationPathRow::nodePathJson))
            .map(row -> {
                UserAuthorization authorization = new UserAuthorization();
                authorization.setNodeId(row.nodeId());
                authorization.setAuthorization(AuthLevel.fromValue(row.authorization()));
                authorization.setNodePath(parseNodePath(row.nodePathJson()));
                return authorization;
            })
            .toList();
    }

    @Transactional
    public void anonymizeAccount(UUID userId) {
        userProfile(userId);
        userService.scrubUser(userId);
    }

    @Transactional
    public Optional<RegistrationResult> completeRegistration(SessionData sessionData) {
        OffsetDateTime now = OffsetDateTime.now();

        if (!sessionData.bootstrap()) {
            Optional<com.trawhile.domain.PendingInvitation> invitation =
                pendingInvitationRepository.findById(sessionData.invitationId());
            if (invitation.isEmpty() || !invitation.get().userId().equals(sessionData.userId())) {
                return Optional.empty();
            }
        }

        if (sessionData.bootstrap() && !userRepository.existsById(sessionData.userId())) {
            userRepository.save(new User(sessionData.userId(), now));
        }

        if (!sessionData.bootstrap() && !userRepository.existsById(sessionData.userId())) {
            throw new EntityNotFoundException("User", sessionData.userId());
        }

        UserProfile profile = userProfileRepository.save(new UserProfile(
            UUID.randomUUID(),
            sessionData.userId(),
            sessionData.name(),
            null
        ));
        oauthProviderRepository.save(new UserOauthProvider(
            UUID.randomUUID(),
            profile.id(),
            sessionData.provider(),
            sessionData.subject()
        ));

        if (sessionData.bootstrap()) {
            authorizationRepository.save(new NodeAuthorization(
                UUID.randomUUID(),
                rootNodeId(),
                sessionData.userId(),
                com.trawhile.domain.AuthLevel.ADMIN
            ));
        } else {
            pendingInvitationRepository.deleteById(sessionData.invitationId());
        }

        sseDispatcher.dispatch(sessionData.userId(),
            new SseEvent(SseEvent.EventType.AUTHORIZATION_CHANGE, Map.of("userId", sessionData.userId())));

        return Optional.of(new RegistrationResult(
            sessionData.userId(),
            sessionData.provider(),
            configuredPrivacyNoticeUrl()
                .filter(ignored -> authorizationService.hasAnyAuthorization(sessionData.userId()))
                .map(URI::create)
                .orElse(null)
        ));
    }

    private Optional<String> configuredPrivacyNoticeUrl() {
        String configured = trawhileConfig.getPrivacyNoticeUrl();
        if (configured == null || configured.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(configured);
    }

    private UserProfile userProfile(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User", userId);
        }
        return userProfileRepository.findByUserId(userId)
            .orElseThrow(() -> new EntityNotFoundException("UserProfile", userId));
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize report settings", ex);
        }
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse report settings payload", ex);
        }
    }

    private List<NodePathEntry> parseNodePath(String nodePathJson) {
        try {
            return objectMapper.readerForListOf(NodePathEntry.class).readValue(nodePathJson);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse node path payload", ex);
        }
    }

    private UUID rootNodeId() {
        return nodeRepository.findAll().stream()
            .filter(node -> node.parentId() == null)
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Root node not found"))
            .id();
    }
}
