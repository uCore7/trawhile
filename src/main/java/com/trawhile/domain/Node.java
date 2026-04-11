package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Table("nodes")
public record Node(
    @Id UUID id,
    UUID parentId,
    String name,
    String description,
    boolean isActive,
    int sortOrder,
    OffsetDateTime createdAt,
    OffsetDateTime deactivatedAt,
    String color,          // CSS hex e.g. '#4A90D9'; nullable
    String icon,           // PrimeIcons identifier e.g. 'pi-briefcase'; nullable
    byte[] logo,           // uploaded image, max 256 KB; nullable — served via GET /nodes/{id}/logo
    String logoMimeType    // MIME type of logo e.g. 'image/png'; nullable
) {}
