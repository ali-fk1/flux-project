package com.flux.fluxproject.repositories;

import com.flux.fluxproject.domain.EmailVerificationToken;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface EmailVerificationTokenRepository extends ReactiveCrudRepository<EmailVerificationToken, UUID> {
    Mono<EmailVerificationToken> findByTokenHash(String tokenHash);

    Mono<Void> deleteByUserId(UUID userId);
}
