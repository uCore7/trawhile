package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/**
 * Personal data for a user. Deleted on anonymization; cascades to UserOauthProvider and QuickAccess.
 */
@Table("user_profile")
public record UserProfile(
    @Id UUID id,
    UUID userId,
    String name,
    boolean gdprNoticeAccepted,  // set true on first-login GDPR notice acknowledgement
    String language,             // IETF tag: en | de | fr | es
    String lastReportSettings    // JSONB — last report filter state; opaque to server
) {}
