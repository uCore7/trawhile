package com.trawhile.service.administration;

import java.util.UUID;

public class InvitationNotFoundException extends RuntimeException {

    public InvitationNotFoundException(UUID invitationId) {
        super("Invitation not found: " + invitationId);
    }
}
