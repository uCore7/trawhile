package com.trawhile.web;

import com.trawhile.service.UserService;
import com.trawhile.web.api.InvitationsApi;
import com.trawhile.web.dto.CreateInvitation201Response;
import com.trawhile.web.dto.CreateInvitationRequest;
import com.trawhile.web.dto.PendingInvitation;
import com.trawhile.web.dto.ResendInvitation200Response;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/** GET/POST/DELETE /api/v1/invitations — invitation management (Node Admin of root only). */
@RestController
@RequestMapping("/api/v1")
public class InvitationController implements InvitationsApi {

    private final UserService userService;

    public InvitationController(UserService userService) {
        this.userService = userService;
    }

    @Override
    public ResponseEntity<List<PendingInvitation>> listInvitations() {
        return ResponseEntity.ok(userService.listInvitations(currentUserId()));
    }

    @Override
    public ResponseEntity<CreateInvitation201Response> createInvitation(CreateInvitationRequest createInvitationRequest) {
        CreateInvitation201Response response = userService.createInvitation(
            currentUserId(),
            createInvitationRequest.getEmail(),
            baseUrl()
        );
        return ResponseEntity.status(201).body(response);
    }

    @Override
    public ResponseEntity<ResendInvitation200Response> resendInvitation(UUID invitationId) {
        return ResponseEntity.ok(userService.resendInvitation(currentUserId(), invitationId, baseUrl()));
    }

    @Override
    public ResponseEntity<Void> withdrawInvitation(UUID invitationId) {
        userService.withdrawInvitation(currentUserId(), invitationId);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId() {
        return UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private String baseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
