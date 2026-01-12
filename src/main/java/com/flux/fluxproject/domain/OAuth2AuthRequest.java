package com.flux.fluxproject.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("oauth2_authorization_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuth2AuthRequest {
    @Id
    private UUID id;

    @Column("provider")
    private String provider;

    @Column("user_id")
    private UUID userId;

    @Column("state")
    private String state;

    @Column("code_verifier")
    private String codeVerifier;

    @Column("created_at")
    private Instant createdAt;

    @Column("expires_at")
    private Instant expiresAt;

    @Column("consumed")
    private boolean consumed;
}
