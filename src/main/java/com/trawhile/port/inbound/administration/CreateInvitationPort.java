package com.trawhile.port.inbound.administration;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public interface CreateInvitationPort {

    record CreateInvitationCommand(UUID actingUserId, String email, String applicationBaseUrl) {}

    record CreatedInvitationView(
            UUID id,
            UUID userId,
            String email,
            Instant invitedAt,
            Instant expiresAt) {}

    record CreateInvitationResult(CreatedInvitationView invitation, URI mailtoUrl) {}

    CreateInvitationResult createInvitation(CreateInvitationCommand command);
}
