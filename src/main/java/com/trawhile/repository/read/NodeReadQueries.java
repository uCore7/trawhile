package com.trawhile.repository.read;

import com.trawhile.domain.AuthLevel;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeReadQueries {

    Optional<NodeSummaryRow> findVisibleRootNodeSummary(UUID actingUserId);

    Optional<NodeSummaryRow> findVisibleNodeSummary(UUID actingUserId, UUID nodeId);

    List<NodeSummaryRow> findVisibleChildren(UUID actingUserId, UUID parentId);

    Optional<NodeContentRow> findVisibleNodeContent(UUID actingUserId, UUID nodeId);

    record NodeSummaryRow(
        UUID id,
        UUID parentId,
        String name,
        String description,
        boolean isActive,
        int sortOrder,
        OffsetDateTime createdAt,
        OffsetDateTime deactivatedAt,
        boolean hasActiveChildren,
        AuthLevel effectiveAuthorization,
        String color,
        String icon,
        boolean hasLogo
    ) {
    }

    record NodeContentRow(
        UUID nodeId,
        byte[] logo,
        String logoMimeType
    ) {
    }
}
