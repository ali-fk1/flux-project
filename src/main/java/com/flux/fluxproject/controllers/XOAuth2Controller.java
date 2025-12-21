package com.flux.fluxproject.controllers;

import com.flux.fluxproject.services.X.XOAuth2Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class XOAuth2Controller {

    private final XOAuth2Service xOAuth2Service;

    @PostMapping("/X")
    public Mono<String> startAuthFlow() {
       return xOAuth2Service.buildAuthorizationUrl();
    }



}
