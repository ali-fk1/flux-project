package com.flux.fluxproject.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final WebClient webClient = WebClient.create("https://api.mailgun.net/v3");
    private final VerificationTokenService verificationTokenService;

    @Value("${mailgun.api-key}")
    private String apiKey;

    @Value("${mailgun.domain}")
    private String domain;

    @Value("${app.backend-url}")
    private String backendUrl;

    private String mask(String s) {
        if (s == null) return null;
        return s.length() <= 10 ? s : s.substring(0, 6) + "..." + s.substring(s.length() - 4);
    }

    public Mono<Void> sendVerificationEmail(UUID userId, String toEmail) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("sendVerificationEmail called with empty toEmail");
            return Mono.error(new IllegalArgumentException("Recipient email is empty"));
        }

        log.info("============================================================");
        log.info("SENDING VERIFICATION EMAIL");
        log.info("User ID: {}", userId);
        log.info("Email: {}", toEmail);
        log.info("============================================================");

        return verificationTokenService
                .createAndSaveTokenForUser(userId)
                .flatMap(rawToken -> {
                    log.info("📧 Raw token generated:");
                    log.info("  Token: {}", rawToken);
                    log.info("  Length: {}", rawToken.length());
                    log.info("  First 10: {}", rawToken.substring(0, Math.min(10, rawToken.length())));
                    log.info("  Last 10: {}", rawToken.substring(Math.max(0, rawToken.length() - 10)));

                    String verificationLink = backendUrl + "/verify?token=" + rawToken;

                    log.info("📧 Verification link created:");
                    log.info("  Full link: {}", verificationLink);
                    log.info("  Masked: {}", mask(verificationLink));

                    final String uri = "/" + domain + "/messages";

                    return webClient.post()
                            .uri(uri)
                            .headers(h -> h.setBasicAuth("api", apiKey))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(BodyInserters
                                    .fromFormData("from", "Flux Scheduler <noreply@" + domain + ">")
                                    .with("to", toEmail)
                                    .with("subject", "Verify your email")
                                    .with("template", "verify-email")
                                    .with("v:verification_url", verificationLink)
                            )
                            .exchangeToMono(response ->
                                    response.bodyToMono(String.class)
                                            .defaultIfEmpty("")
                                            .flatMap(body -> {
                                                if (response.statusCode().is2xxSuccessful()) {
                                                    log.info("✅ Mailgun accepted message for to='{}'. status={}",
                                                            toEmail, response.statusCode());
                                                    return Mono.empty();
                                                } else {
                                                    log.error("❌ Mailgun error for to='{}'. status={}, body='{}'",
                                                            toEmail, response.statusCode(), body);
                                                    return Mono.error(new RuntimeException(
                                                            "Mailgun error: " + response.statusCode() + " - " + body
                                                    ));
                                                }
                                            })
                            );
                }).then();
    }
}