package com.trawhile.port.inbound.administration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListInvitationsPort {

    List<InvitationListItem> list(UUID actingUserId);

    record InvitationListItem(
            UUID id,
            String email,
            UUID inviterId,
            String inviterDisplayName,
            Instant invitedAt,
            Instant expiresAt,
            UUID userId,
            int preAssignedGrantCount) {}
}
