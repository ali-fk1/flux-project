package com.flux.fluxproject.services;

import com.flux.fluxproject.config.KeyManager;
import com.flux.fluxproject.domain.RefreshToken;
import com.flux.fluxproject.repositories.RefreshTokenRepository;
import com.flux.fluxproject.util.VerificationTokenUtil;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final KeyManager keyManager; // symmetric HMAC keys
    private final RefreshTokenRepository refreshTokenRepo;

    public String generateAccessToken(UUID userId, String email) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(60 * 15); // 15 minutes

        return Jwts.builder()
                .setSubject(userId.toString())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .claim("email", email)
                .signWith(keyManager.getAccessTokenKey())
                .compact();
    }

    public Mono<String> createRefreshToken(UUID userId) {
        String rawToken = VerificationTokenUtil.generateRawToken();
        RefreshToken token = new RefreshToken();
//        token.setId(UUID.randomUUID());
        token.setUserId(userId);
        token.setToken(rawToken); // store the raw token directly
        token.setCreatedAt(Instant.now());
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        token.setRevoked(false);

        return refreshTokenRepo.save(token)
                .thenReturn(rawToken);
    }

    public Mono<String> rotateRefreshToken(String rawToken, UUID userId) {
        return refreshTokenRepo.findByToken(rawToken)
                .flatMap(existing -> {
                    if (Boolean.TRUE.equals(existing.getRevoked()) || Instant.now().isAfter(existing.getExpiresAt())) {
                        return Mono.error(new IllegalStateException("Refresh token invalid"));
                    }
                    existing.setRevoked(true);
                    return refreshTokenRepo.save(existing)
                            .then(createRefreshToken(userId));
                });
    }
}


