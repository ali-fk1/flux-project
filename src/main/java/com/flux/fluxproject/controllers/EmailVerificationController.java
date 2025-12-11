package com.flux.fluxproject.controllers;

import com.flux.fluxproject.services.EmailService;
import com.flux.fluxproject.services.EmailVerificationService;
import com.flux.fluxproject.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationController {
    private final EmailVerificationService emailVerificationService;
    private final EmailService emailService;
    private final UserService userService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/verify")
    public Mono<ResponseEntity<Void>> verifyEmail(@RequestParam String token) {
        log.info("============================================================");
        log.info("EMAIL VERIFICATION ATTEMPT");
        log.info("Token received: '{}'", token);
        log.info("Token length: {}", token == null ? 0 : token.length());
        if (token != null && token.length() > 0) {
            log.info("First 10 chars: '{}'", token.substring(0, Math.min(10, token.length())));
            log.info("Last 10 chars: '{}'", token.substring(Math.max(0, token.length() - 10)));
        }
        log.info("============================================================");

        return emailVerificationService.verify(token)
                .then(Mono.fromCallable(() -> {
                    log.info("✅ Email verified successfully, redirecting to success page");
                    return ResponseEntity
                            .status(HttpStatus.FOUND)
                            .header("Location", frontendUrl + "/verification-success")
                            .<Void>build();
                }))
                .onErrorResume(e -> {
                    log.error("❌ Email verification failed: {}", e.getMessage());
                    log.error("Error type: {}", e.getClass().getSimpleName());
                    String errorType = getErrorType(e);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FOUND)
                            .header("Location", frontendUrl + "/verification-failed?error=" + errorType)
                            .<Void>build());
                });
    }

    private String getErrorType(Throwable e) {
        String message = e.getMessage().toLowerCase();
        if (message.contains("expired")) {
            return "expired";
        } else if (message.contains("already used")) {
            return "already-used";
        } else if (message.contains("invalid")) {
            return "invalid";
        }
        return "unknown";
    }

    @PostMapping("/resend-verification")
    public Mono<ResponseEntity<Map<String, String>>> resendVerification(@RequestBody ResendRequest request) {
        log.info("Resend verification email request for: {}", request.getEmail());

        return userService.getUserByEmail(request.getEmail())
                .flatMap(user -> {
                    if (Boolean.TRUE.equals(user.getEnabled())) {
                        log.warn("Resend verification requested for already verified user: {}", request.getEmail());
                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "Email is already verified")));
                    }

                    return emailService.sendVerificationEmail(user.getId(), user.getEmail())
                            .doOnSuccess(v -> log.info("Verification email resent to '{}'", request.getEmail()))
                            .thenReturn(ResponseEntity.ok(
                                    Map.of("message", "Verification email sent successfully")
                            ));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Resend verification requested for non-existent user: {}", request.getEmail());
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "User not found")));
                }))
                .onErrorResume(e -> {
                    log.error("Failed to resend verification email for '{}': {}",
                            request.getEmail(), e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Failed to send verification email")));
                });
    }

    public static class ResendRequest {
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }
}
