package com.flux.fluxproject.model;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
public class SocialAccountDTO {
    private UUID id;
    private UUID userId; // reference to User
    private String platform; // 'twitter', 'facebook', etc.
    private String platformUserId;
    private String username;
    private String authData; // JSONB stored as Map
    private OffsetDateTime expiresAt;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
