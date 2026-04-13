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

    // TODO: implement SR-F043.F01 — getProfile (user profile + linked providers + own authorizations + last report settings)
    // TODO: implement SR-F044.F01 — linkProvider
    // TODO: implement SR-F045.F01 — unlinkProvider (blocked if only one provider)
    // TODO: implement SR-F047.F01 — anonymizeAccount (delegates to UserService.scrubUser() for SR-F070.F01)
    // TODO: implement SR-F066.F01 — saveReportSettings (persist last_report_settings JSONB)
    // TODO: implement SR-F060.F02 — completeRegistration (inserts user_profile + user_oauth_providers, deletes pending_invitations by id)
}
