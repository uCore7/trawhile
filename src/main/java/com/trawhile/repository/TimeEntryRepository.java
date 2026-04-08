package com.trawhile.repository;

import com.trawhile.domain.TimeEntry;
import org.springframework.data.repository.ListCrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeEntryRepository extends ListCrudRepository<TimeEntry, UUID> {

    /** Active entry: ended_at IS NULL. At most one per user (enforced by partial unique index). */
    Optional<TimeEntry> findByUserIdAndEndedAtIsNull(UUID userId);

    List<TimeEntry> findByUserIdOrderByStartedAtDesc(UUID userId);
}
