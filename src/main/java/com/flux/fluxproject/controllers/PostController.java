package com.flux.fluxproject.controllers;

import com.flux.fluxproject.config.KeycloakPrincipalExtractor;
import com.flux.fluxproject.domain.PostStatus;
import com.flux.fluxproject.model.CursorPageResponse;
import com.flux.fluxproject.model.PostViewResponse;
import com.flux.fluxproject.model.UpdatePostRequest;
import com.flux.fluxproject.services.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    private final KeycloakPrincipalExtractor extractor;
    @GetMapping("/posts")
    public Mono<CursorPageResponse<PostViewResponse>> getPosts(
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) String cursor
    ) {
        return extractor.resolveLocalUserId()
                .flatMap(userId -> postService.getPosts(userId, size, status, cursor));
    }

    @DeleteMapping("/posts/{postId}")
    public Mono<ResponseEntity<Void>> deletePost(@PathVariable UUID postId) {
        return extractor.resolveLocalUserId()
                .flatMap(userId -> postService.deletePost(userId, postId))
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @PatchMapping("/posts/{postId}")
    public Mono<ResponseEntity<Void>> updatePost(@PathVariable UUID postId, @RequestBody UpdatePostRequest request) {
        return extractor.resolveLocalUserId()
                .flatMap(userId-> postService.updatePost(userId, postId,request))
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

}
