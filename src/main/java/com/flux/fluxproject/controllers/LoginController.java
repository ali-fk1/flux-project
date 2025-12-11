package com.flux.fluxproject.controllers;

import com.flux.fluxproject.config.CookieUtil;
import com.flux.fluxproject.repositories.RefreshTokenRepository;
import com.flux.fluxproject.repositories.UserRepository;
import com.flux.fluxproject.services.AuthenticationService;
import com.flux.fluxproject.services.TokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class LoginController {

    private final AuthenticationService authenticationService;
    private final TokenService tokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final CookieUtil cookieUtil;

    /**
     * Login endpoint - authenticates user and returns tokens.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, Object>>> login(@RequestBody LoginRequest request) {
        log.info("============================================================");
        log.info("LOGIN REQUEST RECEIVED");
        log.info("Email: {}", request.getEmail());
        log.info("Password provided: {}", request.getPassword() != null && !request.getPassword().isEmpty());
        log.info("============================================================");

        return authenticationService.authenticateUser(request.getEmail(), request.getPassword())
                .doOnSuccess(user -> log.info("✅ User authenticated: {}", user.getEmail()))
                .flatMap(user -> {
                    log.debug("Generating auth response for user: {}", user.getEmail());
                    return authenticationService.generateAuthResponse(user);
                })
                .doOnSuccess(response -> log.debug("✅ Auth response generated"))
                .map(response -> {
                    String refreshToken = (String) response.get("refreshToken");

                    // Remove refresh token from response body (will be in cookie)
                    response.remove("refreshToken");

                    // Create cookie header
                    String cookieHeader = cookieUtil.createRefreshTokenCookie(refreshToken);

                    log.info("✅✅✅ LOGIN SUCCESSFUL");
                    log.info("User: {}", request.getEmail());
                    log.info("Access token length: {}", ((String)response.get("accessToken")).length());
                    log.info("Refresh token cookie set: {}", cookieHeader.substring(0, Math.min(50, cookieHeader.length())) + "...");
                    log.info("Response body keys: {}", response.keySet());
                    log.info("============================================================");

                    return ResponseEntity.ok()
                            .header("Set-Cookie", cookieHeader)
                            .body(response);
                })
                .doOnError(e -> {
                    log.error("============================================================");
                    log.error("❌ LOGIN FAILED");
                    log.error("Email: {}", request.getEmail());
                    log.error("Error type: {}", e.getClass().getSimpleName());
                    log.error("Error message: {}", e.getMessage());
                    log.error("============================================================");
                })
                .onErrorResume(e -> {
                    HttpStatus status = e.getMessage().contains("verify your email")
                            ? HttpStatus.FORBIDDEN
                            : HttpStatus.UNAUTHORIZED;

                    log.warn("Returning error response: {} - {}", status, e.getMessage());
                    return Mono.just(ResponseEntity.status(status)
                            .body(Map.of("error", e.getMessage())));
                });
    }

    /**
     * Refresh endpoint - rotates refresh token and generates new access token.
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public Mono<ResponseEntity<Map<String, Object>>> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {

        log.debug("Token refresh attempt");

        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("Refresh attempt with no token");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No refresh token provided")));
        }

        return refreshTokenRepository.findByToken(refreshToken)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Refresh token not found in database");
                    return Mono.error(new RuntimeException("Invalid refresh token"));
                }))
                .flatMap(token -> {
                    // Check if token is revoked
                    if (Boolean.TRUE.equals(token.getRevoked())) {
                        log.warn("Attempted to use revoked refresh token");
                        return Mono.error(new RuntimeException("Refresh token revoked"));
                    }

                    // Check if token is expired
                    if (Instant.now().isAfter(token.getExpiresAt())) {
                        log.warn("Attempted to use expired refresh token");
                        return Mono.error(new RuntimeException("Refresh token expired"));
                    }

                    // Get user
                    return userRepository.findById(token.getUserId())
                            .flatMap(user -> {
                                // Rotate refresh token
                                return tokenService.rotateRefreshToken(refreshToken, user.getId())
                                        .flatMap(newRefreshToken -> {
                                            // Generate new access token
                                            String newAccessToken = tokenService.generateAccessToken(
                                                    user.getId(), user.getEmail());

                                            Map<String, Object> response = new HashMap<>();
                                            response.put("accessToken", newAccessToken);

                                            // User info
                                            Map<String, Object> userInfo = new HashMap<>();
                                            userInfo.put("id", user.getId().toString());
                                            userInfo.put("email", user.getEmail());
                                            userInfo.put("name", user.getName());
                                            response.put("user", userInfo);

                                            // Create new cookie
                                            String cookieHeader = cookieUtil.createRefreshTokenCookie(newRefreshToken);

                                            log.info("Token refreshed successfully for user: {}", user.getEmail());
                                            return Mono.just(ResponseEntity.ok()
                                                    .header("Set-Cookie", cookieHeader)
                                                    .body(response));
                                        });
                            });
                })
                .onErrorResume(e -> {
                    log.error("Token refresh failed: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("error", e.getMessage())));
                });
    }

    /**
     * Logout endpoint - revokes refresh token and clears cookie.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, String>>> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {

        log.info("Logout attempt");

        if (refreshToken == null || refreshToken.isBlank()) {
            log.debug("Logout called with no refresh token - clearing cookie anyway");
            String cookieHeader = cookieUtil.clearRefreshTokenCookie();
            return Mono.just(ResponseEntity.ok()
                    .header("Set-Cookie", cookieHeader)
                    .body(Map.of("message", "Logged out successfully")));
        }

        return refreshTokenRepository.findByToken(refreshToken)
                .flatMap(token -> {
                    token.setRevoked(true);
                    return refreshTokenRepository.save(token);
                })
                .then(Mono.fromCallable(() -> {
                    String cookieHeader = cookieUtil.clearRefreshTokenCookie();
                    log.info("User logged out successfully");
                    return ResponseEntity.ok()
                            .header("Set-Cookie", cookieHeader)
                            .body(Map.of("message", "Logged out successfully"));
                }))
                .onErrorResume(e -> {
                    log.error("Logout error (non-critical): {}", e.getMessage());
                    // Even if there's an error, clear the cookie
                    String cookieHeader = cookieUtil.clearRefreshTokenCookie();
                    return Mono.just(ResponseEntity.ok()
                            .header("Set-Cookie", cookieHeader)
                            .body(Map.of("message", "Logged out successfully")));
                });
    }

    // DTOs
    public static class LoginRequest {
        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
