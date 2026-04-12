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

    // TODO: implement SR-F004.F01 (list users), SR-F005.F01 (list pending invitations), SR-F006.F01 (invite), SR-F011.F01 (resend invite), SR-F007.F01 (withdraw invite), SR-F008.F01 (remove user)
    // TODO: implement expireInvitations() — called by InvitationExpiryJob (SR-C010.C01)
}
