package com.trawhile.web;

import com.trawhile.service.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET/POST/DELETE /api/v1/invitations — invitation management (Node Admin of root only). */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final UserService userService;

    public InvitationController(UserService userService) {
        this.userService = userService;
    }

    // TODO: implement F1.5–F1.7
}
