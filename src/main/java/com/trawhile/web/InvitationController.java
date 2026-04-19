package com.trawhile.web;

import com.trawhile.service.UserService;
import com.trawhile.web.api.InvitationsApi;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET/POST/DELETE /api/v1/invitations — invitation management (Node Admin of root only). */
@RestController
@RequestMapping("/api/v1")
public class InvitationController implements InvitationsApi {

    private final UserService userService;

    public InvitationController(UserService userService) {
        this.userService = userService;
    }

    // TODO: implement SR-F005.F01 (list pending invitations), SR-F006.F01 (invite user), SR-F007.F01 (withdraw invitation)
}
