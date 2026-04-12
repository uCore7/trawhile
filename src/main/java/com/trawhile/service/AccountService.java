package com.trawhile.service;

import com.trawhile.repository.*;
import com.trawhile.sse.SseDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AccountService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserOauthProviderRepository oauthProviderRepository;
    private final NodeAuthorizationRepository authorizationRepository;
    private final McpTokenRepository mcpTokenRepository;
    private final PendingInvitationRepository pendingInvitationRepository;
    private final SseDispatcher sseDispatcher;

    public AccountService(UserRepository userRepository,
                          UserProfileRepository userProfileRepository,
                          UserOauthProviderRepository oauthProviderRepository,
                          NodeAuthorizationRepository authorizationRepository,
                          McpTokenRepository mcpTokenRepository,
                          PendingInvitationRepository pendingInvitationRepository,
                          SseDispatcher sseDispatcher) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.oauthProviderRepository = oauthProviderRepository;
        this.authorizationRepository = authorizationRepository;
        this.mcpTokenRepository = mcpTokenRepository;
        this.pendingInvitationRepository = pendingInvitationRepository;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement F6.1 — getProfile
    // TODO: implement F6.2 — linkProvider
    // TODO: implement F6.3 — unlinkProvider (blocked if only one provider)
    // TODO: implement F6.4 — getOwnAuthorizations
    // TODO: implement F6.5 — anonymizeAccount (stops tracking, revokes MCP tokens, deletes profile + cascade)
    // TODO: implement SR-043b — saveReportSettings (persist last_report_settings JSONB)
    // TODO: implement SR-057a — completeRegistration (inserts user_profile + user_oauth_providers, deletes pending_invitations by id)
}
