package com.flux.fluxproject.controllers;

import com.flux.fluxproject.model.SignUpRequestBody;
import com.flux.fluxproject.services.EmailService;
import com.flux.fluxproject.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Slf4j
public class SignUpController {

    private final UserService userService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/signup")
    public Mono<ResponseEntity<Void>> handleSignUp(@RequestBody SignUpRequestBody request) {
        final String email = request.email();
        final String pwd = request.password();

        log.info("Signup request received for email='{}', passwordLength={}",
                email, pwd == null ? 0 : pwd.length());

        return userService.registerUser(email, pwd)
                .flatMap(user -> {
                    log.info("User registered successfully. email='{}', id={}",
                            email, user.getId());

                    // FIX: send email with correct userId + email
                    return emailService.sendVerificationEmail(user.getId(), user.getEmail())
                            .doOnSuccess(v ->
                                    log.info("Verification email sent to '{}'", email))
                            .doOnError(e ->
                                    log.error("Failed to send verification email to '{}': {}",
                                            email, e.getMessage()))
                            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).<Void>build());
                })
                .onErrorResume(e -> {
                    log.error("User registration failed for email='{}'. Reason: {}",
                            email, e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }
}
