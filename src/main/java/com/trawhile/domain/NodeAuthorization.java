package com.trawhile.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

@Table("node_authorizations")
public record NodeAuthorization(
    @Id UUID id,
    UUID nodeId,
    UUID userId,
    AuthLevel authorization
) {}
