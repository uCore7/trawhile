package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/** Personal node color preference. Cascade-deleted with UserProfile on anonymization. */
@Table("node_colors")
public record NodeColor(
    @Id UUID id,
    UUID profileId,
    UUID nodeId,
    String color
) {}
