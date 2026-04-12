package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/** OAuth2 provider link. subject is the provider's stable user ID, never an email address. */
@Table("user_oauth_providers")
public record UserOauthProvider(
    @Id UUID id,
    UUID profileId,
    String provider,   // 'google' | 'apple' | 'microsoft' | 'keycloak'
    String subject     // provider's subject ID
) {}
