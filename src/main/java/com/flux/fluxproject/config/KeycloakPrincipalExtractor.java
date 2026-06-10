package com.flux.fluxproject.config;

import com.flux.fluxproject.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class KeycloakPrincipalExtractor {

    private final UserService userService;

    /**
     * Extracts Keycloak sub from the JWT, then resolves (or creates)
     * the local users.id UUID for that sub.
     */
    public Mono<UUID> resolveLocalUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (Jwt) ctx.getAuthentication().getPrincipal())
                .flatMap(jwt -> {
                    String keycloakId = jwt.getSubject();
                    String email = jwt.getClaimAsString("email");
                    String name  = jwt.getClaimAsString("name");
                    return userService.findOrCreateByKeycloakId(keycloakId, email, name)
                            .map(user -> user.getId());
                });
    }

    /** Returns the raw Keycloak sub (for logging etc.) */
    public Mono<String> resolveKeycloakId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> (Jwt) ctx.getAuthentication().getPrincipal())
                .map(Jwt::getSubject);
    }
}