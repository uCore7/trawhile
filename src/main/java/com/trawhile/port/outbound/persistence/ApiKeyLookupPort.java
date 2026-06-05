package com.trawhile.port.outbound.persistence;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound persistence port for API key read-by-hash lookups (SR-00-C08).
 *
 * <p>This is the auth-time lookup surface only. Insert / markRevoked /
 * markLastUsed land in a separate port per SR-08-F03.C02; SR-08 impl
 * briefs implement them.</p>
 */
public interface ApiKeyLookupPort {

    /**
     * Looks up an API key by SHA-256 hash and returns its owner + scope
     * descriptor when the key is currently valid (revoked_at IS NULL AND
     * expires_at &gt; NOW()). Empty otherwise.
     *
     * <p>This is the auth-time lookup surface only. Insert / markRevoked /
     * markLastUsed land in a separate port per SR-08-F03.C02; SR-08 impl
     * briefs implement them.</p>
     */
    Optional<ApiKeyPrincipal> findValidByHash(String sha256HexHash);

    /**
     * Projection of an API key row used solely for building an
     * {@code Authentication} token during request authentication.
     *
     * @param apiKeyId    the primary key of the api_keys row
     * @param userId      the owning user
     * @param scopeNodeId the scope root node for this key
     * @param scopeLevel  the scope level as a string (e.g. "view", "track", "admin")
     */
    record ApiKeyPrincipal(UUID apiKeyId, UUID userId, UUID scopeNodeId, String scopeLevel) {}
}
