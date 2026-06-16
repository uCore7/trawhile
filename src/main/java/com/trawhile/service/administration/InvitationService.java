package com.trawhile.service.administration;

import com.trawhile.adapter.outbound.logging.AppLogger;
import com.trawhile.port.inbound.administration.CreateInvitationPort;
import com.trawhile.port.outbound.persistence.InvitationPersistencePort;
import com.trawhile.service.identity.AuthorizationService;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService implements CreateInvitationPort {

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
