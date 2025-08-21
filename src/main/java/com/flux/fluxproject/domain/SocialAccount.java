package com.flux.fluxproject.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("social_accounts")
public class SocialAccount {
    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId; // reference to User

    private String platform; // 'twitter', 'facebook', etc.

    @Column("platform_user_id")
    private String platformUserId;

    private String username;

    @Column("auth_data")
    private Map<String, Object> authData; // JSONB stored as Map

    @Column("expires_at")
    private OffsetDateTime expiresAt;

    @Column("is_active")
    private Boolean isActive;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
