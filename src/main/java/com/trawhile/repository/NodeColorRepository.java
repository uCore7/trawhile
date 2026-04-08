package com.trawhile.repository;

import com.trawhile.domain.NodeColor;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeColorRepository extends ListCrudRepository<NodeColor, UUID> {

    List<NodeColor> findByProfileId(UUID profileId);

    Optional<NodeColor> findByProfileIdAndNodeId(UUID profileId, UUID nodeId);
}
