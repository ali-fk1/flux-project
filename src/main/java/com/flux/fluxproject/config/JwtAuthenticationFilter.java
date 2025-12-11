    package com.flux.fluxproject.config;

    import io.jsonwebtoken.Claims;
    import io.jsonwebtoken.JwtException;
    import io.jsonwebtoken.Jwts;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.core.io.buffer.DataBuffer;
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
    public class JwtAuthenticationFilter implements WebFilter{

        private final KeyManager keyManager;

        // Paths that don't require authentication
        private static final List<String> PUBLIC_PATHS = List.of(
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/register",
    //            "/api/x",
                "/api/x/callback",     // X OAuth callback
                "/signup",
                "/verify",
                "/resend-verification"
        );

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().toString();

            log.debug("============================================================");
            log.debug("REQUEST INTERCEPTED BY JWT FILTER");
            log.debug("Method: {}", method);
            log.debug("Path: {}", path);
            log.debug("============================================================");

            // Skip authentication for public paths
            if (isPublicPath(path)) {
                log.debug("✅ Public path - skipping authentication: {}", path);
                return chain.filter(exchange);
            }

            // Skip authentication for non-API paths
            if (!path.startsWith("/api/")) {
                log.debug("✅ Non-API path - skipping authentication: {}", path);
                return chain.filter(exchange);
            }

            log.debug("🔒 Protected API path - authentication required");

            // Extract Authorization header
            String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("❌ Missing or invalid Authorization header for path: {}", path);
                log.warn("Authorization header present: {}", authHeader != null);
                if (authHeader != null) {
                    log.warn("Authorization header starts with 'Bearer ': {}", authHeader.startsWith("Bearer "));
                }
                return unauthorized(exchange, "Missing or invalid authorization token");
            }

            String token = authHeader.substring(7); // Remove "Bearer " prefix
            log.debug("Token extracted, length: {}", token.length());
            log.debug("Token preview: {}...", token.substring(0, Math.min(20, token.length())));

            try {
                // Validate and parse JWT
                Claims claims = Jwts.parser()
                        .verifyWith(keyManager.getAccessTokenKey())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // Extract user information
                String userId = claims.getSubject();
                String email = claims.get("email", String.class);

                log.info("✅✅ JWT VALID - User authenticated");
                log.info("User ID: {}", userId);
                log.info("Email: {}", email);
                log.info("Path allowed: {}", path);
                log.info("============================================================");

                // Add user info to exchange attributes for use in controllers
                exchange.getAttributes().put("userId", userId);
                exchange.getAttributes().put("userEmail", email);

                // Continue with the request
                return chain.filter(exchange);

            } catch (JwtException e) {
                log.error("============================================================");
                log.error("❌ JWT VALIDATION FAILED");
                log.error("Path: {}", path);
                log.error("Error type: {}", e.getClass().getSimpleName());
                log.error("Error message: {}", e.getMessage());
                log.error("============================================================");
                return unauthorized(exchange, "Invalid or expired token");
            } catch (Exception e) {
                log.error("============================================================");
                log.error("❌ UNEXPECTED ERROR DURING JWT VALIDATION");
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