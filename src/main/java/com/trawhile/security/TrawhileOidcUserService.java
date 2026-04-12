package com.trawhile.security;

import com.trawhile.domain.*;
import com.trawhile.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Custom OIDC user service for Google and Apple Sign In.
 *
 * Apple-specific notes:
 * - The 'name' claim is only present on the FIRST authorization (subsequent logins omit it).
 * - Store the name from the first login; never attempt to update it from Apple OIDC claims.
 * - The 'email' claim may be a relay address (private relay) — never store it.
 *   Subject (sub) is the stable identifier.
 */
@Service
public class TrawhileOidcUserService extends OidcUserService {

    private final UserOauthProviderRepository oauthProviderRepository;
    private final PendingInvitationRepository pendingInvitationRepository;
    private final NodeRepository nodeRepository;
    private final NodeAuthorizationRepository nodeAuthorizationRepository;
    private final AuthorizationQueries authorizationQueries;

    @Value("${app.bootstrap-admin-email:}")
    private String bootstrapAdminEmail;

    public TrawhileOidcUserService(UserOauthProviderRepository oauthProviderRepository,
                                   PendingInvitationRepository pendingInvitationRepository,
                                   NodeRepository nodeRepository,
                                   NodeAuthorizationRepository nodeAuthorizationRepository,
                                   AuthorizationQueries authorizationQueries) {
        this.oauthProviderRepository = oauthProviderRepository;
        this.pendingInvitationRepository = pendingInvitationRepository;
        this.nodeRepository = nodeRepository;
        this.nodeAuthorizationRepository = nodeAuthorizationRepository;
        this.authorizationQueries = authorizationQueries;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        String subject = oidcUser.getSubject();  // 'sub' claim — stable identifier
        String email = extractEmail(oidcUser);

        HttpSession session = currentSession();

        if (Boolean.TRUE.equals(session.getAttribute("LINKING_PROVIDER"))) {
            // TODO: link provider to authenticated user
            session.removeAttribute("LINKING_PROVIDER");
            return oidcUser;
        }

        Optional<UserOauthProvider> existing = oauthProviderRepository.findByProviderAndSubject(provider, subject);
        if (existing.isPresent()) {
            // user_profile existence guaranteed by cascade — returning user, establish session
            return oidcUser;
        }

        // New login — look up pending_invitations by email to get the pre-created user_id
        Optional<PendingInvitation> pending = email != null
            ? pendingInvitationRepository.findByEmail(email)
            : Optional.empty();

        if (pending.isEmpty()) {
            // Check bootstrap path: no invitation needed for first System Admin
            // TODO: handle bootstrap case (SR-001) — if bootstrap conditions met, store bootstrap session data
            throw new OAuth2AuthenticationException("NO_PENDING_INVITATION");
        }

        // Store {userId, invitationId, provider, subject, name} in session; discard email (C-2)
        // For Apple: name claim only present on first login
        // TODO: store session data and redirect to GDPR notice (SR-008)

        return oidcUser;
    }

    /**
     * Extract email for pending invitation lookup only. Email must NOT be stored after matching.
     * For Apple private relay addresses, still usable for matching the pending invitation.
     */
    private String extractEmail(OidcUser user) {
        Object email = user.getAttribute("email");
        return email != null ? email.toString() : null;
    }

    private HttpSession currentSession() {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest().getSession(true);
    }
}
