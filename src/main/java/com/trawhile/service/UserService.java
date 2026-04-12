package com.trawhile.service;

import com.trawhile.repository.*;
import com.trawhile.sse.SseDispatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final NodeAuthorizationRepository authorizationRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final McpTokenRepository mcpTokenRepository;
    private final PendingInvitationRepository pendingInvitationRepository;
    private final SseDispatcher sseDispatcher;

    public UserService(UserRepository userRepository,
                       UserProfileRepository userProfileRepository,
                       NodeAuthorizationRepository authorizationRepository,
                       TimeEntryRepository timeEntryRepository,
                       McpTokenRepository mcpTokenRepository,
                       PendingInvitationRepository pendingInvitationRepository,
                       SseDispatcher sseDispatcher) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.authorizationRepository = authorizationRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.mcpTokenRepository = mcpTokenRepository;
        this.pendingInvitationRepository = pendingInvitationRepository;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement F1.4–F1.9 (view users, invite, withdraw invite, remove user)
    // TODO: implement expireInvitations() — called by InvitationExpiryJob (SR-009a)
}
