package com.trawhile.web;

import com.trawhile.service.AccountService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** /api/v1/account — profile, OAuth2 provider links, own authorizations, anonymization. */
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // TODO: implement F6.1–F6.5
}
