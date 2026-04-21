package com.trawhile.security;

import com.trawhile.domain.PendingInvitation;
import com.trawhile.domain.UserOauthProvider;
import com.trawhile.domain.UserProfile;
import com.trawhile.repository.AuthorizationQueries;
import com.trawhile.repository.PendingInvitationRepository;
import com.trawhile.repository.UserOauthProviderRepository;
import com.trawhile.repository.UserProfileRepository;
import com.trawhile.service.AccountService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Custom OIDC user service for all configured providers (Google, Apple, Microsoft Entra ID, Keycloak).
 *
 * Apple-specific notes:
 * - The 'name' claim is only present on the FIRST authorization (subsequent logins omit it).
 * - Store the name from the first login; never attempt to update it from Apple OIDC claims.
 * - The 'email' claim may be a relay address (private relay) — never store it.
 *   Subject (sub) is the stable identifier.
 */
@Service
public class TrawhileOidcUserService extends OidcUserService {

    public static final String LINKING_PROVIDER_SESSION_KEY = "LINKING_PROVIDER";
    public static final String LINK_COMPLETE_SESSION_KEY = "LINK_COMPLETE";
    public static final String PENDING_GDPR_SESSION_KEY = "PENDING_GDPR";

    private final UserOauthProviderRepository oauthProviderRepository;
    private final UserProfileRepository userProfileRepository;
    private final PendingInvitationRepository pendingInvitationRepository;
    private final AuthorizationQueries authorizationQueries;
    private final AccountService accountService;

    @Value("${BOOTSTRAP_ADMIN_EMAIL:}")
    private String bootstrapAdminEmail;

    public TrawhileOidcUserService(UserOauthProviderRepository oauthProviderRepository,
                                   UserProfileRepository userProfileRepository,
                                   PendingInvitationRepository pendingInvitationRepository,
                                   AuthorizationQueries authorizationQueries,
                                   AccountService accountService) {
        this.oauthProviderRepository = oauthProviderRepository;
        this.userProfileRepository = userProfileRepository;
        this.pendingInvitationRepository = pendingInvitationRepository;
        this.authorizationQueries = authorizationQueries;
        this.accountService = accountService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        String subject = oidcUser.getSubject();  // 'sub' claim — stable identifier
        String email = extractEmail(oidcUser);

        HttpSession session = currentSession();

        if (isLinkingProvider(session.getAttribute(LINKING_PROVIDER_SESSION_KEY), provider)) {
            UUID userId = currentAuthenticatedUserId();
            accountService.linkProvider(userId, provider, subject);
            session.removeAttribute(LINKING_PROVIDER_SESSION_KEY);
            session.setAttribute(LINK_COMPLETE_SESSION_KEY, Boolean.TRUE);
            return principalForInternalUser(oidcUser, userId);
        }

        Optional<UserOauthProvider> existing = oauthProviderRepository.findByProviderAndSubject(provider, subject);
        if (existing.isPresent()) {
            UserProfile profile = userProfileRepository.findById(existing.get().profileId())
                .orElseThrow(() -> new IllegalStateException(
                    "OIDC provider row " + existing.get().id() + " has no linked profile"));
            return principalForInternalUser(oidcUser, profile.userId());
        }

        if (isBootstrapLogin(email)) {
            UUID userId = UUID.randomUUID();
            session.setAttribute(PENDING_GDPR_SESSION_KEY, Map.of(
                "userId", userId.toString(),
                "provider", provider,
                "subject", subject,
                "name", displayName(oidcUser),
                "bootstrap", Boolean.TRUE
            ));
            return principalForInternalUser(oidcUser, userId);
        }

        // New login — look up pending_invitations by email to get the pre-created user_id
        Optional<PendingInvitation> pending = email != null
            ? pendingInvitationRepository.findByEmail(email)
                .filter(invitation -> invitation.expiresAt() == null || invitation.expiresAt().isAfter(java.time.OffsetDateTime.now()))
            : Optional.empty();

        if (pending.isEmpty()) {
            throw notInvited();
        }

        // Store {userId, invitationId, provider, subject, name} in session; discard email (UR-C006)
        // For Apple: name claim only present on first login
        session.setAttribute(PENDING_GDPR_SESSION_KEY, Map.of(
            "invitationId", pending.get().id().toString(),
            "userId", pending.get().userId().toString(),
            "provider", provider,
            "subject", subject,
            "name", displayName(oidcUser)
        ));
        return principalForInternalUser(oidcUser, pending.get().userId());
    }

    /**
     * Extract email for pending invitation lookup only. Email must NOT be stored after matching.
     * For Apple private relay addresses, still usable for matching the pending invitation.
     */
    private String extractEmail(OidcUser user) {
        Object email = user.getAttribute("email");
        return email != null ? email.toString().trim().toLowerCase(Locale.ROOT) : null;
    }

    private String displayName(OidcUser oidcUser) {
        Object name = oidcUser.getAttribute("name");
        return name != null && !name.toString().isBlank() ? name.toString() : "User";
    }

    private boolean isBootstrapLogin(String email) {
        return email != null
            && !bootstrapAdminEmail.isBlank()
            && bootstrapAdminEmail.equalsIgnoreCase(email)
            && !authorizationQueries.systemAdminExists();
    }

    private OAuth2AuthenticationException notInvited() {
        return new OAuth2AuthenticationException(new OAuth2Error("not_invited"));
    }

    private OidcUser principalForInternalUser(OidcUser oidcUser, UUID userId) {
        Map<String, Object> claims = new HashMap<>(oidcUser.getClaims());
        claims.put("trawhile_user_id", userId.toString());
        return new DefaultOidcUser(
            List.of(new SimpleGrantedAuthority("ROLE_USER")),
            oidcUser.getIdToken(),
            new OidcUserInfo(claims),
            "trawhile_user_id"
        );
    }

    private HttpSession currentSession() {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest().getSession(true);
    }

    private boolean isLinkingProvider(Object sessionValue, String provider) {
        if (sessionValue instanceof String configuredProvider) {
            return configuredProvider.equals(provider);
        }
        return Boolean.TRUE.equals(sessionValue);
    }

    private UUID currentAuthenticatedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error("linking_requires_session"));
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            throw new OAuth2AuthenticationException(new OAuth2Error("linking_requires_session"), ex);
        }
    }
}
