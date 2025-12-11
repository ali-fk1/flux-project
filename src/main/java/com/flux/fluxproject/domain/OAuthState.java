package com.flux.fluxproject.domain;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("oauth_states")
public class OAuthState {

    @Id
    private UUID id;

    private UUID userId;
    private String platform; // "twitter", "facebook", etc.

    private String oauthToken; // request token from OAuth provider
    private String tokenSecret; // secret for the request token

    private Instant createdAt;
    private Instant expiresAt; // OAuth states should expire quickly (5-10 mins)

    private Boolean consumed; // true after callback is processed
}