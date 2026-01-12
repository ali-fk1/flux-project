package com.flux.fluxproject.controllers;

import com.flux.fluxproject.repositories.OAuth2AuthRequestRepository;
import com.flux.fluxproject.services.X.XOAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class XOAuth2Controller {

    private final XOAuth2Service xOAuth2Service;
    private final OAuth2AuthRequestRepository repository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/x")
    public Mono<String> startAuthFlow(ServerWebExchange serverWebExchange) {
        return xOAuth2Service.buildAuthorizationUrl(serverWebExchange);
    }

    @GetMapping("/x/callback")
    public Mono<ResponseEntity<Object>> handleXCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state) {

        // If X sends something unexpected, fail gracefully
        if (code == null || state == null) {
            log.error("Missing code/state in X callback. code={}, state={}", code, state);
            return Mono.just(ResponseEntity
                    .status(HttpStatus.FOUND)
                    .location(frontendRedirectWithMessage("/auth/error", "Missing code or state"))
                    .build());
        }

        return repository.findByState(state)
                .switchIfEmpty(Mono.error(new IllegalStateException("Invalid OAuth state")))
                .flatMap(authRequest -> {

                    if (authRequest.getExpiresAt() != null && authRequest.getExpiresAt().isBefore(Instant.now())) {
                        log.warn("Auth request expired for state: {}", state);
                        return Mono.error(new IllegalStateException("OAuth state expired"));
                    }

                    if (authRequest.isConsumed()) {
                        log.warn("Auth request already consumed for state: {}", state);
                        return Mono.error(new IllegalStateException("OAuth state already used"));
                    }

                    return xOAuth2Service.exchangeCodeForToken(authRequest, code)
                            .flatMap(xTokenResponse ->
                                    xOAuth2Service.saveSocialAccountAndMarkConsumed(authRequest, xTokenResponse));
                })
                .onErrorResume(e -> {
                    log.error("OAuth callback error: ", e);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FOUND)
                            .location(frontendRedirectWithMessage("/auth/error", e.getMessage()))
                            .build());
                });
    }

    @GetMapping("/x/status")
    public Mono<ResponseEntity<Map<String, Object>>> checkXConnectionStatus(ServerWebExchange serverWebExchange) {
        String userIdStr = serverWebExchange.getAttribute("userId");

        if (userIdStr == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        return xOAuth2Service.checkConnectionStatus(userId)
                .map(isConnected -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("connected", isConnected);
                    return ResponseEntity.ok(response);
                });
    }

    private URI frontendRedirectWithMessage(String path, String message) {
        String base = normalizeFrontendBase(frontendUrl);
        String p = normalizePath(path);

        return UriComponentsBuilder
                .fromUriString(base + p)
                .queryParam("message", message == null ? "Unknown error" : message)
                .build()
                .toUri();
    }

    private String normalizeFrontendBase(String url) {
        if (url == null || url.isBlank()) {
            // If you don't set this, your redirects are trash anyway.
            throw new IllegalStateException("Missing app.frontend-url property");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/auth/error";
        return path.startsWith("/") ? path : "/" + path;
    }
}
