package com.trawhile.repository;

import com.trawhile.domain.QuickAccess;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuickAccessRepository extends ListCrudRepository<QuickAccess, UUID> {

    List<QuickAccess> findByProfileIdOrderBySortOrder(UUID profileId);

    Optional<QuickAccess> findByProfileIdAndNodeId(UUID profileId, UUID nodeId);

    void deleteByProfileIdAndNodeId(UUID profileId, UUID nodeId);
}
