package com.flux.fluxproject.repositories;

import com.flux.fluxproject.domain.Post;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface PostRepository extends ReactiveCrudRepository<Post, UUID> {

    @Query("""
WITH due AS (
    SELECT id 
    FROM posts 
    WHERE status = 'scheduled' 
      AND scheduled_at_utc <= now()
    ORDER BY scheduled_at_utc ASC
    FOR UPDATE SKIP LOCKED
    LIMIT :batchSize
)
UPDATE posts p
SET status = 'publishing',
    updated_at_utc = now()
FROM due
WHERE p.id = due.id
RETURNING *
""")
    Flux<Post> claimDuePosts(int batchSize);

    @Query("""
UPDATE posts
SET status = 'published',
    published_at_utc = :publishedAt,
    error_message = NULL,
    updated_at_utc = now()
WHERE id = :postId
RETURNING *
""")
    Mono<Post> markPublished(UUID postId, Instant publishedAt);

    @Query("""
UPDATE posts 
SET status = 'failed',
    error_message = :error,
    retry_count = retry_count + 1,
    updated_at_utc = now()
WHERE id = :postId
RETURNING *
""")
    Mono<Post> markFailed(UUID postId , String error);
}
