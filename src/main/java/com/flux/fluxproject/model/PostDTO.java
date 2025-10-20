package com.flux.fluxproject.model;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class PostDTO {
    private UUID id;
    private UUID userId;
    private Long socialAccountId;
    private String platform; // 'twitter', 'facebook', etc.
    private String content;
    private List<String> mediaUrls; // PostgreSQL text[]
    private Instant scheduledAt;
    private Instant publishedAt;
    private String status; // 'draft', 'scheduled', etc.
    private Map<String, Object> apiPayload; // JSONB
    private String errorMessage;
    private Integer retryCount;
    private Integer maxRetries;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
