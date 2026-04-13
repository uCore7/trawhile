package com.trawhile.web;

import com.trawhile.service.UserService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/v1/users — user list (Node Admin of root only). */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // TODO: implement SR-F004.F01 (list users), SR-F008.F01 (remove user), SR-F011.F01 (resend invite)
}
