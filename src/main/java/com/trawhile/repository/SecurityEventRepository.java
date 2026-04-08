package com.trawhile.repository;

import com.trawhile.domain.SecurityEvent;
import org.springframework.data.repository.ListCrudRepository;

import java.util.UUID;

public interface SecurityEventRepository extends ListCrudRepository<SecurityEvent, UUID> {
}
