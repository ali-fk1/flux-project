package com.flux.fluxproject.schedulers;

import com.flux.fluxproject.repositories.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokensCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(fixedRate =  3000)
    public void refreshTokensCleanupScheduler() {
        log.info("Starting OAuth state cleanup...");

        refreshTokenRepository.deleteByRevokedIsTrue()
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Cleaned up {} Refresh Tokens", count);
                    }
                })
                .doOnError(error -> log.error("Error during Refresh tokens cleanup", error))
                .subscribe();
    }
}