package com.flux.fluxproject.services;

import com.flux.fluxproject.domain.User;
import com.flux.fluxproject.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    /**
     * Finds the local user record for a Keycloak-authenticated user.
     * If no local record exists yet (first login), creates one — JIT provisioning.
     */
    public Mono<User> findOrCreateByKeycloakId(String keycloakId, String email, String name) {
        return userRepository.findByKeycloakId(keycloakId)
                .switchIfEmpty(
                        userRepository.save(
                                User.builder()
                                        .keycloakId(keycloakId)
                                        .email(email)
                                        .name(name)
                                        .createdAt(OffsetDateTime.now())
                                        .updatedAt(OffsetDateTime.now())
                                        .build()
                        )
                );
    }

    public Mono<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
