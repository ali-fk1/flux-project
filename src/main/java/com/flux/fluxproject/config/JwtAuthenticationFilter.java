package com.flux.fluxproject.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

    @Component
    @RequiredArgsConstructor
    @Slf4j
    public class JwtAuthenticationFilter implements WebFilter {

        private final KeyManager keyManager;

        private static final String ACCESS_TOKEN_COOKIE = "access_token";

        private static final List<String> PUBLIC_PATHS = List.of(
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/register",
                "/api/X",
                "/api/x/callback",
                "/signup",
                "/verify",
                "/resend-verification"
        );

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();

            log.debug("============================================================");
            log.debug("🔐 JWT AUTH FILTER INVOKED");
            log.debug("Method: {}", method);
            log.debug("Path: {}", path);
            log.debug("============================================================");

            // Skip authentication for public paths
            if (isPublicPath(path)) {
                log.debug("✅ Public path detected — skipping authentication: {}", path);
                return chain.filter(exchange);
            }

            log.debug("🔒 Protected path — validating JWT from HttpOnly cookie");

            // 🍪 Extract JWT from HttpOnly cookie
            HttpCookie cookie = exchange.getRequest()
                    .getCookies()
                    .getFirst(ACCESS_TOKEN_COOKIE);

            if (cookie == null || cookie.getValue().isBlank()) {
                log.warn("❌ Missing access token COOKIE for path: {}", path);
                log.warn("Cookie name expected: {}", ACCESS_TOKEN_COOKIE);
                return unauthorized(exchange, "Missing authentication cookie");
            }

            String token = cookie.getValue();

            log.debug("🍪 Access token cookie found (length={})", token.length());
            log.debug("🍪 Token preview: {}...", token.substring(0, Math.min(20, token.length())));

            try {
                Claims claims = Jwts.parser()
                        .verifyWith(keyManager.getAccessTokenKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                String email = claims.get("email", String.class);

                log.info("✅ JWT VALID (via HttpOnly cookie)");
                log.info("User ID: {}", userId);
                log.info("Email: {}", email);
                log.info("Access granted for path: {}", path);
                log.info("============================================================");

                exchange.getAttributes().put("userId", userId);
                exchange.getAttributes().put("userEmail", email);

                return chain.filter(exchange);

            } catch (JwtException e) {
                log.error("============================================================");
                log.error("❌ JWT VALIDATION FAILED (cookie-based)");
                log.error("Path: {}", path);
                log.error("Error type: {}", e.getClass().getSimpleName());
                log.error("Error message: {}", e.getMessage());
                log.error("============================================================");
                return unauthorized(exchange, "Invalid or expired token");
            } catch (Exception e) {
                log.error("============================================================");
                log.error("❌ UNEXPECTED ERROR DURING COOKIE-BASED JWT VALIDATION");
                log.error("Path: {}", path);
                log.error("Error: {}", e.getMessage(), e);
                log.error("============================================================");
                return unauthorized(exchange, "Authentication failed");
            }
        }

        private boolean isPublicPath(String path) {
            return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
        }

        private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            String jsonResponse = String.format("{\"error\":\"%s\"}", message);
            DataBuffer buffer = response.bufferFactory()
                    .wrap(jsonResponse.getBytes(StandardCharsets.UTF_8));

            log.warn("🚫 Returning 401 Unauthorized: {}", message);
            return response.writeWith(Mono.just(buffer));
        }
    }