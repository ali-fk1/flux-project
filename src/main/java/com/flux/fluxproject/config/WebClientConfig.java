package com.flux.fluxproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient xWebClient(WebClient.Builder builder) {
        return builder.baseUrl("https://api.x.com")
                .defaultHeader("Accept", "application/json")
                .build();
    }
}
