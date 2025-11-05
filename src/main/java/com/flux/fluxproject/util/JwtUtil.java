package com.flux.fluxproject.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey key;
    private static final long EXPIRATION_MINUTES = 30;

    public JwtUtil(@Value("${aes.auth-secret-key}") String secret) {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalArgumentException("JWT secret key must not be null or empty");
        }
        // Decode Base64 key (recommended for 256-bit keys)
        byte[] keyBytes = Base64.getDecoder().decode(secret.trim());
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    // For email verification (short-lived, purpose=email_verification)
    public String generateVerificationToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(15 * 60))) // 15 minutes for verification
                .claim("purpose", "email_verification")
                .signWith(key)
                .compact();
    }

    // For login sessions (purpose=login)
    public String generateLoginToken(String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(EXPIRATION_MINUTES * 60)))
                .claim("purpose", "login")
                .signWith(key)
                .compact();
    }

    // Validate email verification token (used in /verify endpoint)
    public String validateVerificationToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!"email_verification".equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Invalid token purpose");
        }

        return claims.getSubject();
    }

    // Validate login token (used in auth filter)
    public String validateLoginToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!"login".equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Invalid token purpose");
        }

        return claims.getSubject();
    }
}