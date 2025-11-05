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

        // Log arrival of request: don't log plaintext password, only length
        log.info("Signup request received for email='{}', passwordLength={}", email, pwd == null ? 0 : pwd.length());

        return userService.registerUser(email, pwd)
                .flatMap(user -> {
                    log.info("User registered successfully for email='{}', id={}", email, user.getId());

                    // Chain the email send so it actually runs and we can observe its result.
                    return emailService.sendVerificationEmail(email)
                            .doOnSuccess(v -> log.info("sendVerificationEmail completed (Mailgun request triggered) for email='{}'", email))
                            .doOnError(e -> log.error("sendVerificationEmail failed for email='{}'. Reason: {}", email, e.getMessage()))
                            // Return ResponseEntity only after the send attempt completes (optional: you can .onErrorResume to still return CREATED)
                            .thenReturn(ResponseEntity.status(HttpStatus.CREATED).<Void>build());
                })
                .onErrorResume(e -> {
                    // Log registration error and respond 400
                    log.error("User registration failed for email='{}'. Reason: {}", email, e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }
}
