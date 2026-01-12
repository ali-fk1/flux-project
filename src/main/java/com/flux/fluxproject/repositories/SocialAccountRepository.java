package com.flux.fluxproject.repositories;

import com.flux.fluxproject.domain.SocialAccount;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface SocialAccountRepository extends ReactiveCrudRepository<SocialAccount, UUID> {
//    Flux<SocialAccount> findByPlatformAndPlatformUserId(String platform, String platformUserId);

    // ALTERNATIVE - Get first record only (most recent)
    @Query("SELECT * FROM social_accounts " +
            "WHERE platform = :platform AND platform_user_id = :platformUserId " +
            "ORDER BY created_at DESC LIMIT 1")
    Mono<SocialAccount> findFirstByPlatformAndPlatformUserId(String platform, String platformUserId);

    Mono<SocialAccount> findByUserIdAndPlatform(UUID userId, String platform);
    Mono<SocialAccount> findByPlatformAndPlatformUserId(String platform, String platformUserId);

    Mono<Void> deleteByUserId(UUID userId);
}
