package com.flux.fluxproject.domain;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.List;
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
    private UUID socialAccountId;

    @Column("platform")
    private String platform; // 'X', 'Instagram', etc.

    @Column("content")
    private String content;

    @Column("media_urls")
    private List<String> mediaUrls; // PostgreSQL text[]

    @Column("scheduled_at_utc")
    private Instant scheduledAtUtc;

    @Column("published_at_utc")
    private Instant publishedAtUtc;

    @Column("status")
    private PostStatus status; // 'draft', 'scheduled', etc.

    @Column("api_payload")
    private String apiPayload; // JSONB

    @Column("error_message")
    private String errorMessage;

    @Column("retry_count")
    private Integer retryCount;

    @Column("max_retries")
    private Integer maxRetries;

    @Column("created_at_utc")
    private Instant createdAtUtc;

    @Column("updated_at_utc")
    private Instant updatedAtUtc;
}
