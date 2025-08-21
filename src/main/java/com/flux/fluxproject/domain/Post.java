package com.flux.fluxproject.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("posts")
public class Post {
    @Id
    private UUID id;

    @Column("user_id")
    private UUID userId;

    @Column("social_account_id")
    private Long socialAccountId;

    private String platform; // 'twitter', 'facebook', etc.

    private String content;

    @Column("media_urls")
    private List<String> mediaUrls; // PostgreSQL text[]

    @Column("scheduled_at")
    private OffsetDateTime scheduledAt;

    @Column("published_at")
    private OffsetDateTime publishedAt;

    private String status; // 'draft', 'scheduled', etc.

    @Column("api_payload")
    private Map<String, Object> apiPayload; // JSONB

    @Column("error_message")
    private String errorMessage;

    @Column("retry_count")
    private Integer retryCount;

    @Column("max_retries")
    private Integer maxRetries;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
