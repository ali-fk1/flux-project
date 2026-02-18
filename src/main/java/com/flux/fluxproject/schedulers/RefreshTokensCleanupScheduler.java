package com.flux.fluxproject.schedulers;

import com.flux.fluxproject.repositories.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefreshTokensCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Scheduled(fixedDelay =  30000)
    public void refreshTokensCleanupScheduler() {
        if (!running.compareAndSet(false,true)){
            log.warn("Cleanup already running, skipping this tick");
            return;
        }
        refreshTokenRepository.deleteByExpiresAtBeforeOrRevokedIsTrue(Instant.now())
                .doOnSubscribe(s->log.info("Starting OAuth state cleanup..."))
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Cleaned up {} Refresh Tokens", count);
                    }
                })
                .doOnError(error -> log.error("Error during Refresh tokens cleanup", error))
                .doFinally(sig->running.set(false))
                .subscribe();
    }
}