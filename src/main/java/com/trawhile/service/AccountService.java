package com.trawhile.service;

import com.trawhile.repository.NodeAuthorizationRepository;
import com.trawhile.repository.UserOauthProviderRepository;
import com.trawhile.repository.UserProfileRepository;
import com.trawhile.repository.UserRepository;
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
    private final SseDispatcher sseDispatcher;

    public AccountService(UserRepository userRepository,
                          UserProfileRepository userProfileRepository,
                          UserOauthProviderRepository oauthProviderRepository,
                          NodeAuthorizationRepository authorizationRepository,
                          SseDispatcher sseDispatcher) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.oauthProviderRepository = oauthProviderRepository;
        this.authorizationRepository = authorizationRepository;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement F6.1–F6.5 (view profile, link/unlink provider, view authorizations, anonymize)
}
