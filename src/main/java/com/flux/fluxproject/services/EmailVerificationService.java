package com.flux.fluxproject.services;

import com.flux.fluxproject.repositories.EmailVerificationTokenRepository;
import com.flux.fluxproject.repositories.UserRepository;
import com.flux.fluxproject.util.VerificationTokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepo;
    private final UserRepository userRepo;

    public Mono<Void> verify(String rawToken) {
        log.debug("------------------------------------------------------------");
        log.debug("Starting verification process");
        log.debug("Raw token received: '{}'", rawToken);
        log.debug("Raw token length: {}", rawToken == null ? 0 : rawToken.length());

        String hash = VerificationTokenUtil.sha256Hex(rawToken);
        log.debug("Generated hash: {}", hash);
        log.debug("Hash length: {}", hash.length());
        log.debug("------------------------------------------------------------");

        return tokenRepo.findByTokenHash(hash)
                .switchIfEmpty(Mono.defer(() -> {
                    log.error("❌ TOKEN NOT FOUND IN DATABASE");
                    log.error("Looking for hash: {}", hash);
                    log.error("This means either:");
                    log.error("  1. The token was never created");
                    log.error("  2. The token in the URL doesn't match what was saved");
                    log.error("  3. The token was already used and deleted");
                    return Mono.error(new IllegalArgumentException("Invalid token"));
                }))
                .flatMap(token -> {
                    log.debug("✅ Token found in database!");
                    log.debug("Token details:");
                    log.debug("  - ID: {}", token.getId());
                    log.debug("  - User ID: {}", token.getUserId());
                    log.debug("  - Created: {}", token.getCreatedAt());
                    log.debug("  - Expires: {}", token.getExpiresAt());
                    log.debug("  - Used: {}", token.getUsed());
                    log.debug("  - Used At: {}", token.getUsedAt());

                    if (Boolean.TRUE.equals(token.getUsed())) {
                        log.warn("❌ Token already used at: {}", token.getUsedAt());
                        return Mono.error(new IllegalStateException("Token already used"));
                    }

                    Instant now = Instant.now();
                    if (now.isAfter(token.getExpiresAt())) {
                        log.warn("❌ Token expired. Expires: {}, Now: {}", token.getExpiresAt(), now);
                        return Mono.error(new IllegalStateException("Token expired"));
                    }

                    log.debug("✅ Token is valid. Marking as used and enabling user...");
                    token.setUsed(true);
                    token.setUsedAt(Instant.now());

                    return tokenRepo.save(token)
                            .doOnSuccess(t -> log.debug("✅ Token marked as used"))
                            .then(userRepo.findById(token.getUserId()))
                            .flatMap(user -> {
                                log.info("✅ Enabling user: {} ({})", user.getEmail(), user.getId());
                                user.setEnabled(true);
                                return userRepo.save(user);
                            })
                            .doOnSuccess(u -> log.info("✅✅✅ User enabled successfully: {}", u.getEmail()))
                            .then();
                });
    }
}