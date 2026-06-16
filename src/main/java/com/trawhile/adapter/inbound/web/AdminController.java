package com.trawhile.adapter.inbound.web;

import com.trawhile.adapter.inbound.web.api.AdminApi;
import com.trawhile.adapter.inbound.web.dto.ApiAdminInvitationsPostRequest;
import com.trawhile.adapter.inbound.web.dto.InlineObject3;
import com.trawhile.adapter.inbound.web.dto.Invitation;
import com.trawhile.adapter.inbound.web.dto.Problem;
import com.trawhile.adapter.inbound.web.dto.UserReference;
import com.trawhile.port.inbound.administration.CreateInvitationPort;
import com.trawhile.port.inbound.administration.ListInvitationsPort;
import com.trawhile.port.inbound.administration.ResendInvitationPort;
import com.trawhile.port.inbound.administration.WithdrawInvitationPort;
import com.trawhile.service.administration.InvitationConflictException;
import com.trawhile.service.administration.InvitationNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AdminController implements AdminApi {

    private final CreateInvitationPort createInvitationPort;
    private final ListInvitationsPort listInvitationsPort;
    private final ResendInvitationPort resendInvitationPort;
    private final WithdrawInvitationPort withdrawInvitationPort;
    private final HttpServletRequest httpServletRequest;

    public AdminController(
            CreateInvitationPort createInvitationPort,
            ListInvitationsPort listInvitationsPort,
            ResendInvitationPort resendInvitationPort,
            WithdrawInvitationPort withdrawInvitationPort,
            HttpServletRequest httpServletRequest) {
        this.createInvitationPort = createInvitationPort;
        this.listInvitationsPort = listInvitationsPort;
        this.resendInvitationPort = resendInvitationPort;
        this.withdrawInvitationPort = withdrawInvitationPort;
        this.httpServletRequest = httpServletRequest;
    }

    @Override
    public ResponseEntity<List<Invitation>> apiAdminInvitationsGet() {
        UUID actingUserId = resolveActingUserId();

        List<Invitation> invitations = listInvitationsPort.list(actingUserId)
                .stream()
                .map(AdminController::toInvitationDto)
                .toList();

        return ResponseEntity.ok(invitations);
    }

    @Override
    public ResponseEntity<InlineObject3> apiAdminInvitationsPost(
            ApiAdminInvitationsPostRequest request) {
        UUID actingUserId = resolveActingUserId();

        String baseUrl = buildBaseUrl(httpServletRequest);

        CreateInvitationPort.CreateInvitationResult result =
                createInvitationPort.createInvitation(
                        new CreateInvitationPort.CreateInvitationCommand(
                                actingUserId, request.getEmail(), baseUrl));

        Invitation invitationDto = new Invitation(
                result.invitation().id(),
                result.invitation().email(),
                result.invitation().invitedAt(),
                result.invitation().expiresAt(),
                result.invitation().userId());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new InlineObject3(invitationDto, result.mailtoUrl()));
    }

    @Override
    public ResponseEntity<InlineObject3> apiAdminInvitationsIdResendPost(UUID id) {
        UUID actingUserId = resolveActingUserId();
        String baseUrl = buildBaseUrl(httpServletRequest);
        ResendInvitationPort.ResendInvitationResult result = resendInvitationPort.resend(
                new ResendInvitationPort.ResendInvitationCommand(actingUserId, id, baseUrl));
        Invitation dto = new Invitation(
                result.invitation().id(),
                result.invitation().email(),
                result.invitation().invitedAt(),
                result.invitation().expiresAt(),
                result.invitation().userId());
        return ResponseEntity.ok(new InlineObject3(dto, result.mailtoUrl()));
    }

    @Override
    public ResponseEntity<Void> apiAdminInvitationsIdDelete(UUID id) {
        UUID actingUserId = resolveActingUserId();
        withdrawInvitationPort.withdraw(
                new WithdrawInvitationPort.WithdrawInvitationCommand(actingUserId, id));
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(InvitationNotFoundException.class)
    public ResponseEntity<Problem> handleInvitationNotFound(InvitationNotFoundException ex) {
        Problem problem = new Problem()
                .status(404)
                .code("invitation.not_found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }

    private static Invitation toInvitationDto(
            ListInvitationsPort.InvitationListItem item) {
        Invitation invitation = new Invitation(
                item.id(),
                item.email(),
                item.invitedAt(),
                item.expiresAt(),
                item.userId())
                .preAssignedGrantCount(item.preAssignedGrantCount());

        if (item.inviterId() != null) {
            invitation.invitedBy(new UserReference(item.inviterId())
                    .displayName(item.inviterDisplayName()));
        }

        return invitation;
    }

    /**
     * Reads the acting user's UUID from the current HTTP session's
     * {@code trawhile.userId} attribute (the contract set by the OIDC
     * callback flow per SR-01-F13.F01). Throws HTTP 401 if either the
     * session is absent or the attribute is missing/wrong type, so that
     * a confusing NPE deep in the service layer is replaced with a
     * diagnosable client-visible failure mode.
     */
    private UUID resolveActingUserId() {
        HttpSession session = httpServletRequest.getSession(false);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "no authenticated session");
        }
        Object userId = session.getAttribute("trawhile.userId");
        if (!(userId instanceof UUID uuid)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "session is missing trawhile.userId");
        }
        return uuid;
    }

    private static String buildBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("https".equals(scheme) && port == 443)
                || ("http".equals(scheme) && port == 80);
        return defaultPort
                ? scheme + "://" + host
                : scheme + "://" + host + ":" + port;
    }

    @ExceptionHandler(InvitationConflictException.class)
    public ResponseEntity<Problem> handleInvitationConflict(InvitationConflictException ex) {
        Problem problem = new Problem()
                .status(409)
                .code(ex.getConflictCode());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(problem);
    }
}
