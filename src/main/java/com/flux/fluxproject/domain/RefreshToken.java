package com.flux.fluxproject.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    private UUID userId;
    private String token; // hashed token stored here

    private Instant createdAt;
    private Instant expiresAt;

    private Boolean revoked;
}

