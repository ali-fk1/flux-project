package com.flux.fluxproject.controllers;

import com.flux.fluxproject.config.KeycloakPrincipalExtractor;
import com.flux.fluxproject.model.ScheduledPostRequest;
import com.flux.fluxproject.services.X.SchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class PostSchedulingController {

    private final SchedulingService schedulingService;
    private final KeycloakPrincipalExtractor extractor;

    @PostMapping("/schedule")
    public Mono<ResponseEntity<Void>> schedulePost(@RequestBody ScheduledPostRequest request) {
        if (request.getText() == null || request.getText().isBlank() || request.getScheduledAtUtc() == null) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return extractor.resolveLocalUserId()
                .flatMap(userId -> schedulingService.saveScheduledPost(request, userId))
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }
}
