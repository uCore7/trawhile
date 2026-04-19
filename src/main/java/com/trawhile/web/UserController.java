package com.trawhile.web;

import com.trawhile.service.UserService;
import com.trawhile.web.api.UsersApi;
import com.trawhile.web.dto.UserAuthorization;
import com.trawhile.web.dto.UserSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** GET /api/v1/users — user list (Node Admin of root only). */
@RestController
@RequestMapping("/api/v1")
public class UserController implements UsersApi {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Override
    public ResponseEntity<List<UserSummary>> listUsers() {
        return ResponseEntity.ok(userService.listUsers(currentUserId()));
    }

    @Override
    public ResponseEntity<Void> removeUser(UUID userId) {
        userService.removeUser(currentUserId(), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<List<UserAuthorization>> getUserAuthorizations(UUID userId) {
        return ResponseEntity.ok(userService.getUserAuthorizations(currentUserId(), userId));
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }
}
