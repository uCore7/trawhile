package com.trawhile.repository;

import com.trawhile.domain.Node;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

public interface NodeRepository extends ListCrudRepository<Node, UUID> {

    List<Node> findByParentIdOrderBySortOrder(UUID parentId);

    List<Node> findByParentIdAndIsActiveOrderBySortOrder(UUID parentId, boolean isActive);
}
