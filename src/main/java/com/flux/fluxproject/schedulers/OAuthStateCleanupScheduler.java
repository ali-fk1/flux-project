package com.flux.fluxproject.schedulers;

import com.flux.fluxproject.repositories.OAuthStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuthStateCleanupScheduler {

    private final OAuthStateRepository oauthStateRepository;

    /**
     * Clean up expired OAuth states every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void cleanupExpiredStates() {
        log.info("Starting OAuth state cleanup...");

        oauthStateRepository.deleteByExpiresAtBefore(Instant.now())
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Cleaned up {} expired OAuth states", count);
                    }
                })
                .doOnError(error -> log.error("Error during OAuth state cleanup", error))
                .subscribe();
    }
}