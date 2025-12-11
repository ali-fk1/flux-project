package com.flux.fluxproject.domain;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Table("email_verification_tokens")
@Getter
@Setter
public class EmailVerificationToken {
    @Id
    private UUID id;
    private UUID userId;
    private String tokenHash; // sha256 hex
    private Instant createdAt;
    private Instant expiresAt;
    private Boolean used;
    private Instant usedAt;
    private String ipAddress; // nullable, optional
}
