package com.flux.fluxproject.repositories;

import com.flux.fluxproject.domain.RefreshToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

public interface RefreshTokenRepository extends ReactiveCrudRepository<RefreshToken, UUID> {
    Mono<RefreshToken> findByToken(String token);

    Mono<Long> deleteByExpiresAtBefore(Instant expirationTime);

    Mono<Long> deleteByRevokedIsTrue();

    Mono<Long> deleteByExpiresAtBeforeOrRevokedIsTrue(Instant now);


    Mono<Void> deleteByUserId(UUID userId);
}
