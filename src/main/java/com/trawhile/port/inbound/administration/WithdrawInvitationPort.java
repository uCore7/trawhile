package com.trawhile.port.inbound.administration;

import java.util.UUID;

public interface WithdrawInvitationPort {

    void withdraw(WithdrawInvitationCommand command);

    record WithdrawInvitationCommand(UUID actingUserId, UUID invitationId) {}
}
