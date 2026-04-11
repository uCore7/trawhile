package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Singleton row. Always read via CompanySettingsRepository.findSingleton(). */
@Table("company_settings")
public record CompanySettings(
    @Id UUID id,
    String name,
    String timezone,             // IANA timezone
    LocalDate freezeDate,        // entries before this date are immutable; nullable
    int retentionYears,          // >= 2
    int nodeRetentionExtraYears, // >= 0
    String purgeSchedule,        // 'annual' | 'semi_annual' | 'quarterly'
    String privacyNoticeUrl      // HTTPS URL; shown to authenticated+authorized users; nullable
) {}
