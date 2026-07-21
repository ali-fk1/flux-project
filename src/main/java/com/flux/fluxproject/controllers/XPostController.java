package com.flux.fluxproject.controllers;

import com.flux.fluxproject.config.KeycloakPrincipalExtractor;
import com.flux.fluxproject.exceptions.XAccountNotConnectedException;
import com.flux.fluxproject.exceptions.XPostException;
import com.flux.fluxproject.exceptions.XTokenRefreshFailedException;
import com.flux.fluxproject.model.PostTextRequest;
import com.flux.fluxproject.model.XPostResponse;
import com.flux.fluxproject.services.X.XPostService;
import com.flux.fluxproject.services.X.XPublishingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class XPostController {

    private final XPostService xPostService;
    private final KeycloakPrincipalExtractor extractor;
    private final XPublishingService xPublishingService;

    @PostMapping("/post")
    public Mono<ResponseEntity<XPostResponse>> postText(@RequestBody PostTextRequest request) {
        return extractor.resolveLocalUserId()
                .flatMap(userId -> xPublishingService.publishNow(userId, request.getText())
                        .map(response -> {
                            log.info("Successfully posted tweet for userId: {}", userId);
                            return ResponseEntity
                                    .status(HttpStatus.CREATED)
                                    .body(response);
                        })
                        .onErrorResume(XAccountNotConnectedException.class, e -> {
                            log.error("X account not connected for userId: {}", userId);
                            return Mono.just(ResponseEntity
                                    .status(HttpStatus.UNAUTHORIZED)
                                    .<XPostResponse>build());
                        })
                        .onErrorResume(XTokenRefreshFailedException.class, e -> {
                            log.error("Token refresh failed for userId: {}", userId);
                            return Mono.just(ResponseEntity
                                    .status(HttpStatus.UNAUTHORIZED)
                                    .<XPostResponse>build());
                        })
                        .onErrorResume(XPostException.class, e -> {
                            log.error("Failed to post tweet for userId: {}", userId, e);
                            return Mono.just(ResponseEntity
                                    .status(HttpStatus.BAD_GATEWAY)
                                    .<XPostResponse>build());
                        })
                        .onErrorResume(Exception.class, e -> {
                            log.error("Unexpected error for userId: {}", userId, e);
                            return Mono.just(ResponseEntity
                                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .<XPostResponse>build());
                        })
                );
    }

    @GetMapping("/expired")
    public Mono<ResponseEntity<Object>> getExpired() {
        return extractor.resolveLocalUserId()
                .flatMap(userId -> xPostService.checkAccessTokenExpiry(userId)
                        .flatMap(expired -> {
                            if (expired) {
                                log.info("Token expired for userId: {}", userId);
                                return Mono.just(ResponseEntity.ok().body("Access Token Expired"));
                            } else {
                                log.info("Token not expired for userId: {}", userId);
                                return Mono.just(ResponseEntity.ok().body("Access Token Not Expired"));
                            }
                        })
                );
    }
}