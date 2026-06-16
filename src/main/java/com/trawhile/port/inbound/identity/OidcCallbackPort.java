package com.trawhile.port.inbound.identity;

import java.util.Optional;
import java.util.UUID;

public interface OidcCallbackPort {

    CallbackOutcome handle(OidcCallbackCommand command);

    record OidcCallbackCommand(
            String providerRegistrationId,
            String subject,
            String email,
            Optional<UUID> existingAuthenticatedUserId) {}

    sealed interface CallbackOutcome
            permits BootstrapEligible, InvitationMatched, ProviderLinked,
                    KnownIdentityLogin, Rejected {
        UUID userId();
    }

    record BootstrapEligible(UUID userId) implements CallbackOutcome {}

    record InvitationMatched(UUID userId) implements CallbackOutcome {}

    record ProviderLinked(UUID userId) implements CallbackOutcome {}

    record KnownIdentityLogin(UUID userId) implements CallbackOutcome {}

    record Rejected(String redirectUrl, RejectionCause cause) implements CallbackOutcome {
        @Override
        public UUID userId() {
            return null;
        }
    }

    enum RejectionCause {
        NOT_INVITED
    }
}
