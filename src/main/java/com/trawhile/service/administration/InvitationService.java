package com.trawhile.service.administration;

import com.trawhile.adapter.outbound.logging.AppLogger;
import com.trawhile.port.inbound.administration.CreateInvitationPort;
import com.trawhile.port.inbound.administration.ListInvitationsPort;
import com.trawhile.port.inbound.administration.ResendInvitationPort;
import com.trawhile.port.outbound.persistence.InvitationPersistencePort.ResendableInvitation;
import com.trawhile.port.outbound.persistence.InvitationPersistencePort;
import com.trawhile.service.identity.AuthorizationService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService implements CreateInvitationPort, ListInvitationsPort, ResendInvitationPort {

    private static final AppLogger log = AppLogger.getLogger(InvitationService.class);

    private final InvitationPersistencePort invitationPersistencePort;
    private final AuthorizationService authorizationService;

    public InvitationService(
            InvitationPersistencePort invitationPersistencePort,
            AuthorizationService authorizationService) {
        this.invitationPersistencePort = invitationPersistencePort;
        this.authorizationService = authorizationService;
    }

    @Override
    @Transactional
    public CreateInvitationResult createInvitation(CreateInvitationCommand command) {
        authorizationService.checkAdminOnRoot(command.actingUserId());

        if (invitationPersistencePort.existsNonExpiredPendingInvitationByEmail(command.email())) {
            throw new InvitationConflictException("invitation.pending_already_exists");
        }
        if (invitationPersistencePort.existsNonAnonymisedUserByEmail(command.email())) {
            throw new InvitationConflictException("invitation.user_already_exists");
        }

        InvitationPersistencePort.CreatedInvitationRow row =
                invitationPersistencePort.createPendingUserAndInvitation(
                        command.email(), command.actingUserId());

        log.info("invitation_created",
                new AppLogger.UserId(command.actingUserId()),
                new AppLogger.RawField("pendingUserId", row.userId().toString()),
                new AppLogger.RawField("invitationId", row.id().toString()));

        URI mailtoUri = buildMailtoUri(command.email(), command.applicationBaseUrl());

        return new CreateInvitationResult(
                new CreatedInvitationView(
                        row.id(),
                        row.userId(),
                        row.email(),
                        row.invitedAt(),
                        row.expiresAt()),
                mailtoUri);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvitationListItem> list(UUID actingUserId) {
        authorizationService.checkAdminOnRoot(actingUserId);

        return invitationPersistencePort.listAllPendingWithInviterAndGrantCount()
                .stream()
                .map(row -> new InvitationListItem(
                        row.id(),
                        row.email(),
                        row.inviterId(),
                        row.inviterDisplayName(),
                        row.invitedAt(),
                        row.expiresAt(),
                        row.userId(),
                        row.preAssignedGrantCount()))
                .toList();
    }

    @Override
    @Transactional
    public ResendInvitationResult resend(ResendInvitationCommand command) {
        authorizationService.checkAdminOnRoot(command.actingUserId());

        ResendableInvitation row = invitationPersistencePort.findById(command.invitationId())
                .orElseThrow(() -> new InvitationNotFoundException(command.invitationId()));

        ResendableInvitation refreshed =
                invitationPersistencePort.refreshExpiresAtToNinetyDaysFromNow(command.invitationId());

        log.info("invitation_resent",
                new AppLogger.UserId(command.actingUserId()),
                new AppLogger.RawField("invitationId", row.id().toString()),
                new AppLogger.RawField("pendingUserId", row.userId().toString()));

        URI mailtoUri = buildMailtoUri(refreshed.email(), command.applicationBaseUrl());

        return new ResendInvitationResult(
                new ResentInvitationView(
                        refreshed.id(),
                        refreshed.userId(),
                        refreshed.email(),
                        refreshed.invitedAt(),
                        refreshed.expiresAt()),
                mailtoUri);
    }

    private static URI buildMailtoUri(String email, String baseUrl) {
        String subject = "Invitation to trawhile";
        String body = "You have been invited to join trawhile.\n\n"
                + "Please sign in with your oidc identity provider at:\n"
                + baseUrl + "\n\n"
                + "Your invitation email address is: " + email;

        String encodedSubject = URLEncoder.encode(subject, StandardCharsets.UTF_8)
                .replace("+", "%20");
        String encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8)
                .replace("+", "%20");

        return URI.create("mailto:" + email
                + "?subject=" + encodedSubject
                + "&body=" + encodedBody);
    }
}
