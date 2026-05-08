package com.trawhile.repository.read;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface RequestReadQueries {

    List<RequestRow> findVisibleRequests(UUID actingUserId, UUID subtreeRootNodeId);

    List<RequestRow> findVisibleRequestsOnNode(UUID actingUserId, UUID nodeId);

    record RequestRow(
        UUID id,
        UUID requesterId,
        String requesterName,
        UUID nodeId,
        String template,
        String body,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime resolvedAt,
        UUID resolvedBy,
        String resolvedByName
    ) {
    }
}
