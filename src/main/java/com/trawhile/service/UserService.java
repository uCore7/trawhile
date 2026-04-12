package com.trawhile.service;

import com.trawhile.repository.NodeAuthorizationRepository;
import com.trawhile.repository.TimeEntryRepository;
import com.trawhile.repository.UserProfileRepository;
import com.trawhile.repository.UserRepository;
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
    private final SseDispatcher sseDispatcher;

    public UserService(UserRepository userRepository,
                       UserProfileRepository userProfileRepository,
                       NodeAuthorizationRepository authorizationRepository,
                       TimeEntryRepository timeEntryRepository,
                       SseDispatcher sseDispatcher) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.authorizationRepository = authorizationRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.sseDispatcher = sseDispatcher;
    }

    // TODO: implement F1.4–F1.9 (view users, invite, withdraw invite, remove user)
}
