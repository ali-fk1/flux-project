package com.flux.fluxproject.repositories;

import com.flux.fluxproject.domain.SocialAccount;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SocialAccountRepository extends ReactiveCrudRepository<SocialAccount, UUID> {
    Mono<SocialAccount> findByPlatformAndPlatformUserId (String platform, String platformUserId);
}
