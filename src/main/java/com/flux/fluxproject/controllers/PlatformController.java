package com.flux.fluxproject.controllers;

import com.flux.fluxproject.config.KeycloakPrincipalExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class PlatformController {

    private final KeycloakPrincipalExtractor extractor;

    @GetMapping("/me")
    public Mono<Map<String, Object>> me() {
        return extractor.resolveLocalUserId()
                .map(userId -> Map.of("id", userId.toString()));
    }

    @GetMapping("/test")
    public String test() {
        return "hello";
    }
}