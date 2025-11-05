package com.flux.fluxproject.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flux.fluxproject.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final WebClient webClient = WebClient.create("https://api.mailgun.net/v3");
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper; // Injected by Spring Boot

    @Value("${mailgun.api-key}")
    private String apiKey;

    @Value("${mailgun.domain}")
    private String domain;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    /**
     * Helper to mask a token for logs: shows first 6 chars then "..."
     */
    private String mask(String s) {
        if (s == null) return null;
        return s.length() <= 10 ? s : s.substring(0, 6) + "..." + s.substring(s.length() - 4);
    }

    public Mono<Void> sendVerificationEmail(String toEmail) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("sendVerificationEmail called with empty toEmail");
            return Mono.error(new IllegalArgumentException("Recipient email is empty"));
        }

        String token = jwtUtil.generateVerificationToken(toEmail);
        String verificationLink = frontendUrl + "/verify?token=" + token;

        // Log high-level info but never log full token or API key
        log.debug("Preparing verification email for '{}' — verificationLink(masked)='{}', domain='{}'", toEmail, mask(verificationLink), domain);

        String variablesJson;
        try {
            variablesJson = objectMapper.writeValueAsString(Map.of("verification_url", verificationLink));
        } catch (Exception e) {
            log.error("Failed to serialize Mailgun variables for email='{}'", toEmail, e);
            return Mono.error(new RuntimeException("Email serialization failed", e));
        }

        // Build request (we log the endpoint and that we are about to call)
        final String uri = "/" + domain + "/messages";
        log.debug("Calling Mailgun API URI='{}' for to='{}' (not logging API key)", uri, toEmail);

        return webClient.post()
                .uri(uri)
                .headers(h -> {
                    // don't log header content (sensitive), but set auth
                    h.setBasicAuth("api", apiKey);
                })
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters
                        .fromFormData("from", "Flux Scheduler <noreply@" + domain + ">")
                        .with("to", toEmail)
                        .with("subject", "Verify your email")
                        .with("template", "verify-email")
                        .with("h:X-Mailgun-Variables", variablesJson)
                        .with("v:verification_url", verificationLink)
                )
                .exchangeToMono(response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("") // avoid empty mono issues
                                .flatMap(body -> {
                                    if (response.statusCode().is2xxSuccessful()) {
                                        log.info("Mailgun accepted message for to='{}'. status={}, bodySummary='{}'",
                                                toEmail, response.statusCode(), mask(body));
                                        return Mono.empty();
                                    } else {
                                        log.error("Mailgun responded with error for to='{}'. status={}, body='{}'",
                                                toEmail, response.statusCode(), body);
                                        return Mono.error(new RuntimeException("Mailgun error: " + response.statusCode() + " - " + body));
                                    }
                                })
                )
                .doOnError(e -> log.error("Failed to send verification email to '{}': {}", toEmail, e.getMessage()))
                .onErrorResume(e -> {
                    // Fallback plain text send (keeps logs)
                    log.warn("Attempting fallback plain-text send for '{}'", toEmail);
                    return webClient.post()
                            .uri(uri)
                            .headers(h -> h.setBasicAuth("api", apiKey))
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .body(BodyInserters
                                    .fromFormData("from", "Flux Scheduler <noreply@" + domain + ">")
                                    .with("to", toEmail)
                                    .with("subject", "Verify your email (fallback)")
                                    .with("text", "Click: " + verificationLink)
                            )
                            .exchangeToMono(resp ->
                                    resp.bodyToMono(String.class)
                                            .defaultIfEmpty("")
                                            .flatMap(body -> {
                                                if (resp.statusCode().is2xxSuccessful()) {
                                                    log.info("Mailgun fallback accepted for to='{}'. status={}, bodySummary='{}'",
                                                            toEmail, resp.statusCode(), mask(body));
                                                    return Mono.empty();
                                                } else {
                                                    log.error("Mailgun fallback failed for to='{}'. status={}, body='{}'",
                                                            toEmail, resp.statusCode(), body);
                                                    return Mono.error(new RuntimeException("Mailgun fallback error: " + resp.statusCode() + " - " + body));
                                                }
                                            })
                            );
                })
                .then();
    }

}
