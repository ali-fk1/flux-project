package com.flux.fluxproject.controllers;

import com.flux.fluxproject.exceptions.XAccountNotConnectedException;
import com.flux.fluxproject.exceptions.XPostException;
import com.flux.fluxproject.exceptions.XTokenRefreshFailedException;
import com.flux.fluxproject.model.PostTextRequest;
import com.flux.fluxproject.model.XPostResponse;
import com.flux.fluxproject.services.X.XPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class XPostController {

    private final XPostService xPostService;

    @PostMapping("/post")
    public Mono<ResponseEntity<XPostResponse>> postText(
            @RequestBody PostTextRequest request,
            ServerWebExchange exchange) {

        UUID userId = UUID.fromString(exchange.getAttribute("userId").toString());

        return xPostService.postTextWithAutoRefresh(userId, request.getText())
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
                            .build());
                })
                .onErrorResume(XTokenRefreshFailedException.class, e -> {
                    log.error("Token refresh failed for userId: {}", userId);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.UNAUTHORIZED)
                            .build());
                })
                .onErrorResume(XPostException.class, e -> {
                    log.error("Failed to post tweet for userId: {}", userId, e);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.BAD_GATEWAY)
                            .build());
                })
                .onErrorResume(Exception.class, e -> {
                    log.error("Unexpected error for userId: {}", userId, e);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }

    @GetMapping("/expired")
    public Mono<ResponseEntity<Object>> GetExpired(ServerWebExchange exchange) {
        UUID userId = UUID.fromString(exchange.getAttributes().get("userId").toString());
        return xPostService.checkAccessTokenExpiry(userId).flatMap(response->
        {
            if (response == true) {
                log.info("Token expired");
                return Mono.just(ResponseEntity.ok().body("Access Token Expired"));
            }
            else {
                log.info("Token is not expired");
                return Mono.just(ResponseEntity.ok().body("Access Token Not Expired"));
            }
        });

    }

}
