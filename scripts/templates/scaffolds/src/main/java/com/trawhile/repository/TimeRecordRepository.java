package com.trawhile.repository;

import com.trawhile.domain.TimeRecord;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeRecordRepository extends ListCrudRepository<TimeRecord, UUID> {

    /** Active record: ended_at IS NULL. At most one per user (enforced by partial unique index). */
    Optional<TimeRecord> findByUserIdAndEndedAtIsNull(UUID userId);

    List<TimeRecord> findByUserIdOrderByStartedAtDesc(UUID userId);
}
