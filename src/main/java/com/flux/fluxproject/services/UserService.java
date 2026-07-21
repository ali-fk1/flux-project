package com.flux.fluxproject.services;

import com.flux.fluxproject.domain.User;
import com.flux.fluxproject.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    private final UserRepository userRepository;

    /**
     * Finds the local user record for a Keycloak-authenticated user.
     * If no local record exists for this keycloakId, checks if one exists
     * by email (e.g. re-registration / Keycloak identity drift) and re-links it.
     * Otherwise creates a new local record — JIT provisioning.
     */
    public Mono<User> findOrCreateByKeycloakId(String keycloakId, String email, String name) {
        return userRepository.findByKeycloakId(keycloakId)
                .switchIfEmpty(
                        userRepository.findByEmail(email)
                                .flatMap(existingUser -> {
                                    log.warn("Re-linking existing user (email={}) from keycloakId {} to {}",
                                            email, existingUser.getKeycloakId(), keycloakId);
                                    existingUser.setKeycloakId(keycloakId);
                                    existingUser.setName(name);
                                    existingUser.setUpdatedAt(OffsetDateTime.now());
                                    return userRepository.save(existingUser);
                                })
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
                                )
                );
    }

    public Mono<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}