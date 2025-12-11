package com.flux.fluxproject.services;

import com.flux.fluxproject.domain.User;
import com.flux.fluxproject.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Authenticates a user with email and password.
     *
     * @param email User's email
     * @param password User's plain text password
     * @return Mono of authenticated User
     * @throws RuntimeException if authentication fails
     */
    public Mono<User> authenticateUser(String email, String password) {
        log.debug("Authenticating user with email: {}", email);

        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Authentication failed: User not found with email: {}", email);
                    return Mono.error(new RuntimeException("Invalid credentials"));
                }))
                .flatMap(user -> {
                    // Check if email is verified
                    if (!Boolean.TRUE.equals(user.getEnabled())) {
                        log.warn("Authentication failed: Email not verified for user: {}", email);
                        return Mono.error(new RuntimeException("Please verify your email first"));
                    }

                    // Verify password
                    if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                        log.warn("Authentication failed: Invalid password for user: {}", email);
                        return Mono.error(new RuntimeException("Invalid credentials"));
                    }

                    log.info("User authenticated successfully: {}", email);
                    return Mono.just(user);
                });
    }

    /**
     * Generates authentication response with access token, refresh token, and user info.
     *
     * @param user Authenticated user
     * @return Mono of Map containing accessToken, refreshToken, and user info
     */
    public Mono<Map<String, Object>> generateAuthResponse(User user) {
        log.debug("Generating auth response for user: {}", user.getEmail());

        // Generate access token
        String accessToken = tokenService.generateAccessToken(user.getId(), user.getEmail());

        // Generate refresh token
        return tokenService.createRefreshToken(user.getId())
                .map(refreshToken -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("accessToken", accessToken);
                    response.put("refreshToken", refreshToken);

                    // User info (excluding sensitive data)
                    Map<String, Object> userInfo = new HashMap<>();
                    userInfo.put("id", user.getId().toString());
                    userInfo.put("email", user.getEmail());
                    userInfo.put("name", user.getName());
                    response.put("user", userInfo);

                    log.info("Auth response generated for user: {}", user.getEmail());
                    return response;
                });
    }
}