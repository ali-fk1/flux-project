package com.flux.fluxproject.controllers;

import com.flux.fluxproject.domain.PostStatus;
import com.flux.fluxproject.model.CursorPageResponse;
import com.flux.fluxproject.model.PostViewResponse;
import com.flux.fluxproject.services.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;

    @GetMapping("/posts")
    public Mono<CursorPageResponse<PostViewResponse>> getPosts(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) String cursor,
            ServerWebExchange exchange
    ) {


        UUID userId = UUID.fromString(exchange.getAttribute("userId").toString());
        log.info("calling service for id {}",userId);
        return postService.getPosts(
                userId,
                size,
                status,
                cursor
        );
    }

    @DeleteMapping("/posts/{postId}")
    public Mono<ResponseEntity<Void>> deletePost(@PathVariable UUID postId , ServerWebExchange exchange) {
        UUID userId = UUID.fromString(exchange.getAttribute("userId").toString());
        return postService.deletePost(userId , postId).thenReturn(ResponseEntity.noContent().build());
    }

}
