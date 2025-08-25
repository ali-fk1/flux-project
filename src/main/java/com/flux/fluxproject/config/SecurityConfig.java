package com.flux.fluxproject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    // this configuration is for the sake of testing and will be enhanced later
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
                .authorizeExchange(auth -> auth
                        .anyExchange().permitAll()
                )
                .csrf(csrf -> csrf.disable()) // Disable CSRF for dev (or use state param only)
                .formLogin(formLogin -> formLogin.disable()) // No login page
                .httpBasic(httpBasic -> httpBasic.disable()); // No HTTP Basic

        return http.build();
    }
}