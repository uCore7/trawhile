package com.trawhile.service;

import com.trawhile.config.TrawhileConfig;
import com.trawhile.domain.NodeAuthorization;
import com.trawhile.domain.User;
import com.trawhile.domain.UserOauthProvider;
import com.trawhile.domain.UserProfile;
import com.trawhile.exception.EntityNotFoundException;
import com.trawhile.sse.SseEvent;
import com.trawhile.repository.*;
import com.trawhile.sse.SseDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class AccountService {

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
    private final AuthorizationService authorizationService;
    private final NodeRepository nodeRepository;
    private final TrawhileConfig trawhileConfig;
    private final SseDispatcher sseDispatcher;

    public AccountService(UserRepository userRepository,
                          UserProfileRepository userProfileRepository,
                          UserOauthProviderRepository oauthProviderRepository,
                          NodeAuthorizationRepository authorizationRepository,
                          McpTokenRepository mcpTokenRepository,
                          PendingInvitationRepository pendingInvitationRepository,
                          AuthorizationService authorizationService,
                          NodeRepository nodeRepository,
                          TrawhileConfig trawhileConfig,
                          SseDispatcher sseDispatcher) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.oauthProviderRepository = oauthProviderRepository;
        this.authorizationRepository = authorizationRepository;
        this.mcpTokenRepository = mcpTokenRepository;
        this.pendingInvitationRepository = pendingInvitationRepository;
        this.authorizationService = authorizationService;
        this.nodeRepository = nodeRepository;
        this.trawhileConfig = trawhileConfig;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement SR-F043.F01 — getProfile (user profile + linked providers + own authorizations + last report settings)
    // TODO: implement SR-F044.F01 — linkProvider
    // TODO: implement SR-F045.F01 — unlinkProvider (blocked if only one provider)
    // TODO: implement SR-F047.F01 — anonymizeAccount (delegates to UserService.scrubUser() for SR-F070.F01)
    // TODO: implement SR-F066.F01 — saveReportSettings (persist last_report_settings JSONB)
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

    private UUID rootNodeId() {
        return nodeRepository.findAll().stream()
            .filter(node -> node.parentId() == null)
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Root node not found"))
            .id();
    }
}
