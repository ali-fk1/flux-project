package com.flux.fluxproject.controllers;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/public")
    public String publicEndpoint() {
        return "public";
    }

    @GetMapping("/private")
    public Map<String, Object> privateEndpoint(
            JwtAuthenticationToken authentication
    ) {
        return Map.of(
                "subject", authentication.getToken().getSubject(),
                "email", authentication.getToken().getClaimAsString("email")
        );
    }
}