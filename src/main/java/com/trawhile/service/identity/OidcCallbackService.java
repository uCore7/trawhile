package com.trawhile.service.identity;

import com.trawhile.adapter.outbound.logging.AppLogger;
import com.trawhile.port.inbound.identity.OidcCallbackPort;
import com.trawhile.port.outbound.persistence.PendingInvitationLookupPort;
import com.trawhile.port.outbound.persistence.RootAdminLookupPort;
import com.trawhile.port.outbound.persistence.UserActivationPort;
import com.trawhile.port.outbound.persistence.UserOauthProviderLookupPort;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OidcCallbackService implements OidcCallbackPort {

    private static final AppLogger log = AppLogger.getLogger(OidcCallbackService.class);
    private static final String REJECTED_REDIRECT_URL = "/login?error=not_invited";

    private final UserOauthProviderLookupPort userOauthProviderLookupPort;
    private final PendingInvitationLookupPort pendingInvitationLookupPort;
    private final RootAdminLookupPort rootAdminLookupPort;
    private final UserActivationPort userActivationPort;
    private final String bootstrapAdminEmail;

    public OidcCallbackService(
            UserOauthProviderLookupPort userOauthProviderLookupPort,
            PendingInvitationLookupPort pendingInvitationLookupPort,
            RootAdminLookupPort rootAdminLookupPort,
            UserActivationPort userActivationPort,
            @Value("${BOOTSTRAP_ADMIN_EMAIL:}") String bootstrapAdminEmail) {
        this.userOauthProviderLookupPort = userOauthProviderLookupPort;
        this.pendingInvitationLookupPort = pendingInvitationLookupPort;
        this.rootAdminLookupPort = rootAdminLookupPort;
        this.userActivationPort = userActivationPort;
        this.bootstrapAdminEmail = bootstrapAdminEmail;
    }

    @Override
    @Transactional
    public CallbackOutcome handle(OidcCallbackCommand command) {
        if (command.existingAuthenticatedUserId().isPresent()) {
            UUID userId = command.existingAuthenticatedUserId().orElseThrow();
            userOauthProviderLookupPort.insert(
                    userId, command.providerRegistrationId(), command.subject());
            userActivationPort.refreshEmail(userId, command.email());
            logSucceeded(userId, command.providerRegistrationId(), "provider_linking");
            return new ProviderLinked(userId);
        }

        var linkedUserId = userOauthProviderLookupPort.findUserId(
                command.providerRegistrationId(), command.subject());
        if (linkedUserId.isPresent()) {
            UUID userId = linkedUserId.orElseThrow();
            userActivationPort.refreshEmail(userId, command.email());
            logSucceeded(userId, command.providerRegistrationId(), "known_identity_login");
            return new KnownIdentityLogin(userId);
        }

        var invitedUserId = pendingInvitationLookupPort.findUserIdByEmail(command.email());
        if (invitedUserId.isPresent()) {
            UUID userId = invitedUserId.orElseThrow();
            userOauthProviderLookupPort.insert(
                    userId, command.providerRegistrationId(), command.subject());
            pendingInvitationLookupPort.deleteByUserId(userId);
            userActivationPort.refreshEmail(userId, command.email());
            logSucceeded(userId, command.providerRegistrationId(), "invitation_match");
            return new InvitationMatched(userId);
        }

        if (isBootstrapEligible(command.email())) {
            UUID userId = UUID.randomUUID();
            userActivationPort.insertActiveUser(
                    userId, displayNameFromEmail(command.email()), command.email());
            userOauthProviderLookupPort.insert(
                    userId, command.providerRegistrationId(), command.subject());
            rootAdminLookupPort.grantAdminOnRoot(userId);
            log.info("oidc_login_succeeded",
                    new AppLogger.UserId(userId),
                    new AppLogger.RawField("provider", command.providerRegistrationId()),
                    new AppLogger.RawField("path", "bootstrap"),
                    new AppLogger.RawField("bootstrap", "true"));
            return new BootstrapEligible(userId);
        }

        log.info("oidc_login_rejected",
                new AppLogger.RawField("cause", "not_invited"));
        return new Rejected(REJECTED_REDIRECT_URL, RejectionCause.NOT_INVITED);
    }

    private boolean isBootstrapEligible(String email) {
        return !bootstrapAdminEmail.isBlank()
                && bootstrapAdminEmail.equals(email)
                && !rootAdminLookupPort.anyAdminExists();
    }

    private static String displayNameFromEmail(String email) {
        int atIndex = email.indexOf('@');
        String localPart = atIndex > 0 ? email.substring(0, atIndex) : email;
        return localPart.isBlank()
                ? "Bootstrap Admin"
                : localPart.toLowerCase(Locale.ROOT);
    }

    private static void logSucceeded(UUID userId, String provider, String path) {
        log.info("oidc_login_succeeded",
                new AppLogger.UserId(userId),
                new AppLogger.RawField("provider", provider),
                new AppLogger.RawField("path", path));
    }
}
