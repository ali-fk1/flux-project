package com.flux.fluxproject.config;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

@Component
public class KeyManager {

    private final SecretKey accessTokenKey;
    private final SecretKey refreshTokenKey;

    public KeyManager(
            @Value("${jwt.access-secret}") String accessSecret,
            @Value("${jwt.refresh-secret}") String refreshSecret
    ) {
        this.accessTokenKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(accessSecret));
        this.refreshTokenKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(refreshSecret));
    }

    public SecretKey getAccessTokenKey() {
        return accessTokenKey;
    }

    public SecretKey getRefreshTokenKey() {
        return refreshTokenKey;
    }
}

