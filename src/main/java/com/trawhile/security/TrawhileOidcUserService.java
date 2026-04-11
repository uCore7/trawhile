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
 *
 * Shares the same login/linking branching logic as OAuth2UserService (GitHub).
 */
@Service
public class TrawhileOidcUserService extends OidcUserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserOauthProviderRepository oauthProviderRepository;
    private final PendingMembershipRepository pendingMembershipRepository;
    private final NodeRepository nodeRepository;
    private final NodeAuthorizationRepository nodeAuthorizationRepository;
    private final AuthorizationQueries authorizationQueries;

    @Value("${app.bootstrap-admin-email:}")
    private String bootstrapAdminEmail;

    public TrawhileOidcUserService(UserRepository userRepository,
                                   UserProfileRepository userProfileRepository,
                                   UserOauthProviderRepository oauthProviderRepository,
                                   PendingMembershipRepository pendingMembershipRepository,
                                   NodeRepository nodeRepository,
                                   NodeAuthorizationRepository nodeAuthorizationRepository,
                                   AuthorizationQueries authorizationQueries) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.oauthProviderRepository = oauthProviderRepository;
        this.pendingMembershipRepository = pendingMembershipRepository;
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
            // TODO: validate user_profile exists; throw 403 if anonymised/removed
            return oidcUser;
        }

        // New login — check pending membership by email
        Optional<PendingMembership> pending = email != null
            ? pendingMembershipRepository.findByEmail(email)
            : Optional.empty();

        if (pending.isEmpty()) {
            throw new OAuth2AuthenticationException("NO_PENDING_MEMBERSHIP");
        }

        // TODO: run SR-008 registration transaction (create user, user_profile, user_oauth_providers, delete pending)
        // For Apple: extract name from oidcUser.getClaim("name") — only present on first login.
        // TODO: bootstrap check — grant root admin if conditions met

        return oidcUser;
    }

    /**
     * Extract email for pending membership lookup only. Email must NOT be stored after matching.
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
