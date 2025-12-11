package com.flux.fluxproject.services;

import com.flux.fluxproject.domain.EmailVerificationToken;
import com.flux.fluxproject.repositories.EmailVerificationTokenRepository;
import com.flux.fluxproject.util.VerificationTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VerificationTokenService {
    private final EmailVerificationTokenRepository tokenRepository;

    /**
     * Creates a new token for the given userId, saves it hashed in the DB,
     * and returns the raw token to be sent via email.
     */
    public Mono<String> createAndSaveTokenForUser(UUID userId) {
        String rawToken = VerificationTokenUtil.generateRawToken();
        String hashedToken = VerificationTokenUtil.sha256Hex(rawToken);

        EmailVerificationToken token = new EmailVerificationToken();
//        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setTokenHash(hashedToken);
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        token.setUsed(false);

        return tokenRepository.save(token)
                .thenReturn(rawToken);
    }

    /**
     * Validates token by checking hash, expiry, and used flag.
     */
    public Mono<EmailVerificationToken> validateToken(String rawToken) {
        String hashedToken = VerificationTokenUtil.sha256Hex(rawToken);

        return tokenRepository.findByTokenHash(hashedToken)
                .switchIfEmpty(Mono.error(new RuntimeException("Token not found")))
                .flatMap(token -> {
                    if (Boolean.TRUE.equals(token.getUsed())) {
                        return Mono.error(new RuntimeException("Token already used"));
                    }
                    if (token.getExpiresAt().isBefore(Instant.now())) {
                        return Mono.error(new RuntimeException("Token expired"));
                    }
                    return Mono.just(token);
                });
    }

    /**
     * Marks token as used.
     */
    public Mono<EmailVerificationToken> markTokenUsed(String rawToken) {
        return validateToken(rawToken)
                .flatMap(token -> {
                    token.setUsed(true);
                    token.setUsedAt(Instant.now());
                    return tokenRepository.save(token);
                });
    }

}
