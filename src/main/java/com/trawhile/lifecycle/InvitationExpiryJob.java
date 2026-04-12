package com.trawhile.lifecycle;

import com.trawhile.service.UserService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily expiry of pending invitations (SR-009a).
 * For each invitation past its expires_at: executes the SR-009b cleanup via UserService.
 */
@Component
public class InvitationExpiryJob {

    private final UserService userService;

    public InvitationExpiryJob(UserService userService) {
        this.userService = userService;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void expireInvitations() {
        // TODO: userService.expireInvitations()
    }
}
