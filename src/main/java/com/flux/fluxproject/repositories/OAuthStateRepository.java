package com.flux.fluxproject.repositories;

import com.flux.fluxproject.domain.OAuthState;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface OAuthStateRepository extends ReactiveCrudRepository<OAuthState, UUID> {
    Mono<OAuthState> findByOauthTokenAndConsumedFalseAndExpiresAtAfter(
            String oauthToken,
            Instant now
    );

    /**
     * Find by oauth token regardless of consumed status (for cleanup)
     */
    Mono<OAuthState> findByOauthToken(String oauthToken);

    /**
     * Delete expired states (cleanup job)
     */
    Mono<Long> deleteByExpiresAtBefore(Instant expirationTime);

    Mono<OAuthState> findById (UUID id);
}
