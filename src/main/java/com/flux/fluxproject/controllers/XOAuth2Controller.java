package com.flux.fluxproject.controllers;

import com.flux.fluxproject.config.KeycloakPrincipalExtractor;
import com.flux.fluxproject.model.XAccountInfoResponse;
import com.flux.fluxproject.repositories.OAuth2AuthRequestRepository;
import com.flux.fluxproject.services.X.XOAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class XOAuth2Controller {

    private final XOAuth2Service xOAuth2Service;
    private final OAuth2AuthRequestRepository repository;
    private final KeycloakPrincipalExtractor extractor;


    @Value("${app.frontend-url}")
    private String frontendUrl;

    @PostMapping("/x")
    public Mono<String> startWebAuthFlow() {
        return extractor.resolveLocalUserId()
                .flatMap(userId ->
                        xOAuth2Service.buildAuthorizationUrl(userId, "WEB")
                );
    }


    @PostMapping("/x/connect")
    public Mono<ResponseEntity<Map<String, String>>> startMobileAuthFlow() {
        return extractor.resolveLocalUserId()
                .flatMap(userId -> xOAuth2Service.buildAuthorizationUrl(userId, "MOBILE"))
                .map(authUrl -> ResponseEntity.ok(Map.of("authUrl", authUrl)));
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
    public Mono<ResponseEntity<Map<String, Object>>> checkXConnectionStatus() {
        return extractor.resolveLocalUserId()
                .flatMap(userId -> xOAuth2Service.checkConnectionStatus(userId))
                .map(isConnected -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("connected", isConnected);
                    return ResponseEntity.ok(response);
                });
    }

    @GetMapping("/x/account")
    public Mono<ResponseEntity<XAccountInfoResponse>> getXAccountInfo() {
        return extractor.resolveLocalUserId()
                .flatMap(xOAuth2Service::getAccountInfo)
                .map(ResponseEntity::ok);
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
