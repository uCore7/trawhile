package com.trawhile.repository;

import com.trawhile.domain.Request;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.UUID;

public interface RequestRepository extends ListCrudRepository<Request, UUID> {

    List<Request> findByNodeIdOrderByCreatedAtDesc(UUID nodeId);

    List<Request> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);
}
