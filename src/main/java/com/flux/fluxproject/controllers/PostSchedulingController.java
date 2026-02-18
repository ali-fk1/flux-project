package com.flux.fluxproject.controllers;

import com.flux.fluxproject.model.ScheduledPostRequest;
import com.flux.fluxproject.services.X.SchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class PostSchedulingController {

    private final SchedulingService schedulingService;

    @PostMapping("/schedule")
    public Mono<ResponseEntity<Void>> schedulePost(@RequestBody ScheduledPostRequest scheduledPostRequest ,
                                                                   ServerWebExchange serverWebExchange){
        log.info("{}" ,scheduledPostRequest.toString() );
        UUID userId = UUID.fromString(serverWebExchange.getAttribute("userId").toString());
        if(userId == null){
            log.error("Null Id");
            return Mono.just(ResponseEntity.status(401).build());
        }

        if (scheduledPostRequest.getText()==null || scheduledPostRequest.getText().isBlank() || scheduledPostRequest.getScheduledAtUtc() == null){
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return schedulingService.saveScheduledPost(scheduledPostRequest , userId)
                .thenReturn(ResponseEntity.noContent().build());
    }
}
