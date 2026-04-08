package com.trawhile.repository;

import com.trawhile.domain.NodeAuthorization;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NodeAuthorizationRepository extends ListCrudRepository<NodeAuthorization, UUID> {

    List<NodeAuthorization> findByNodeId(UUID nodeId);

    List<NodeAuthorization> findByUserId(UUID userId);

    Optional<NodeAuthorization> findByNodeIdAndUserId(UUID nodeId, UUID userId);

    void deleteByNodeIdAndUserId(UUID nodeId, UUID userId);
}
