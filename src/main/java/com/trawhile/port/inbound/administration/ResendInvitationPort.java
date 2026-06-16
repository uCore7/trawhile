package com.trawhile.port.inbound.administration;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public interface ResendInvitationPort {

    ResendInvitationResult resend(ResendInvitationCommand command);

    record ResendInvitationCommand(UUID actingUserId, UUID invitationId, String applicationBaseUrl) {}

    record ResendInvitationResult(ResentInvitationView invitation, URI mailtoUrl) {}

    record ResentInvitationView(UUID id, UUID userId, String email, Instant invitedAt, Instant expiresAt) {}
}
