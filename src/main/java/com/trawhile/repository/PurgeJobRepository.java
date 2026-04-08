package com.trawhile.repository;

import com.trawhile.domain.PurgeJob;
import org.springframework.data.repository.ListCrudRepository;

import java.util.Optional;
import java.util.UUID;

public interface PurgeJobRepository extends ListCrudRepository<PurgeJob, UUID> {

    Optional<PurgeJob> findByJobType(String jobType);
}
