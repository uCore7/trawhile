package com.trawhile.security;

import com.trawhile.domain.*;
import com.trawhile.repository.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Custom OAuth2UserService implementing login vs. provider-linking branching logic:
 *
 * if HttpSession has attribute "LINKING_PROVIDER" = true:
 *   → link mode: insert user_oauth_providers for the authenticated user
 * else:
 *   → check user_oauth_providers by provider+subject
 *   → if found and user_profile exists: allow login
 *   → if not found: check pending_memberships by email
 *       → if found: run SR-008 registration transaction
 *       → if not found: return 403
 *
 * Bootstrap: after creating a new user, if no root admin exists and
 * BOOTSTRAP_ADMIN_EMAIL matches the provider email, grant admin on root.
 */
@Service
public class OAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserOauthProviderRepository oauthProviderRepository;
    private final PendingMembershipRepository pendingMembershipRepository;
    private final NodeRepository nodeRepository;
    private final NodeAuthorizationRepository nodeAuthorizationRepository;
    private final AuthorizationQueries authorizationQueries;

    @Value("${app.bootstrap-admin-email:}")
    private String bootstrapAdminEmail;

    public OAuth2UserService(UserRepository userRepository,
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
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();
        String subject = extractSubject(provider, oAuth2User);
        String email = extractEmail(oAuth2User);

        HttpSession session = currentSession();

        if (Boolean.TRUE.equals(session.getAttribute("LINKING_PROVIDER"))) {
            // TODO: link provider to authenticated user
            session.removeAttribute("LINKING_PROVIDER");
            return oAuth2User;
        }

        Optional<UserOauthProvider> existing = oauthProviderRepository.findByProviderAndSubject(provider, subject);
        if (existing.isPresent()) {
            // TODO: validate user_profile exists; throw 403 if anonymised/removed
            return oAuth2User;
        }

        // New login — check pending membership
        Optional<PendingMembership> pending = email != null
            ? pendingMembershipRepository.findByEmail(email)
            : Optional.empty();

        if (pending.isEmpty()) {
            throw new OAuth2AuthenticationException("NO_PENDING_MEMBERSHIP");
        }

        // TODO: run SR-008 registration transaction (create user, user_profile, user_oauth_providers, delete pending)
        // TODO: bootstrap check — grant root admin if conditions met

        return oAuth2User;
    }

    private String extractSubject(String provider, OAuth2User user) {
        return switch (provider) {
            case "github" -> user.getAttribute("id").toString();
            case "google" -> user.getAttribute("sub");
            default -> throw new OAuth2AuthenticationException("Unsupported provider: " + provider);
        };
    }

    private String extractEmail(OAuth2User user) {
        Object email = user.getAttribute("email");
        return email != null ? email.toString() : null;
    }

    private HttpSession currentSession() {
        ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attrs.getRequest().getSession(true);
    }
}
