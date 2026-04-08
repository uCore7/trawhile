package com.trawhile.repository;

import com.trawhile.domain.CompanySettings;
import org.springframework.data.repository.ListCrudRepository;

import java.util.UUID;

public interface CompanySettingsRepository extends ListCrudRepository<CompanySettings, UUID> {

    /** Returns the singleton row. Throws if not found (seeded by Flyway). */
    default CompanySettings findSingleton() {
        return findAll().stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("company_settings row missing — check Flyway migration V3"));
    }
}
