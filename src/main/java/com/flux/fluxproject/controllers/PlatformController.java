package com.flux.fluxproject.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class PlatformController {
    @GetMapping("/me")
    public Mono<Map<String, Object>> me(ServerWebExchange exchange) {

        String userId = (String) exchange.getAttributes().get("userId");
        String email  = (String) exchange.getAttributes().get("userEmail");

        if (userId == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        }

        return Mono.just(Map.of(
                "id", userId,
                "email", email
        ));
    }
}