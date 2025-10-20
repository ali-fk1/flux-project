package com.flux.fluxproject.controllers;

import com.flux.fluxproject.services.X.XAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class PlatformController {

    private final XAuthService xAuthService;

    @Value("${x.callback-url}")
    public String callbackUrl;

    // Temporary in-memory storage for request token secrets
    private final Map<String, String> requestTokenSecrets = new ConcurrentHashMap<>();

    /**
     * Step 1: Start X OAuth authentication flow
     * POST /api/x
     */
    @PostMapping("/x")
    public Mono<ResponseEntity<Map<String, String>>> startXAuthFlow() {
        log.info("Starting X OAuth flow with callback URL: {}", callbackUrl);

        return xAuthService.getRequestToken(callbackUrl)
                .doOnNext(response -> log.info("Received request token: {}", response.get("oauth_token")))
                .flatMap(response -> {
                    String oauthToken = response.get("oauth_token");
                    String oauthTokenSecret = response.get("oauth_token_secret");

                    // Store temporarily in memory
                    requestTokenSecrets.put(oauthToken, oauthTokenSecret);
                    log.info("Stored in memory: token={}, secret={}", oauthToken, oauthTokenSecret);

                    String authorizeUrl = xAuthService.buildAuthorizeUrl(oauthToken);
                    log.info("Authorization URL created: {}", authorizeUrl);

                    // Return the URL instead of redirecting
                    Map<String, String> responseBody = new HashMap<>();
                    responseBody.put("authorizeUrl", authorizeUrl);
                    responseBody.put("message", "Please visit the authorization URL");

                    return Mono.just(ResponseEntity.ok(responseBody));
                })
                .doOnError(error -> log.error("Error starting X OAuth flow: ", error))
                .onErrorReturn(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("error", "Failed to start X authentication. Please try again."))
                );
    }

    /**
     * Step 2: Handle callback from X after user authorization
     * GET /api/x/callback?oauth_token=...&oauth_verifier=...
     */
    @GetMapping("/x/callback")
    public Mono<ResponseEntity<String>> handleXCallback(
            @RequestParam("oauth_token") String oauthToken,
            @RequestParam("oauth_verifier") String oauthVerifier) {

        log.info("Handling X OAuth callback - Token: {}, Verifier: {}", oauthToken, oauthVerifier);

        // Get stored token secret from memory
        String tokenSecret = requestTokenSecrets.remove(oauthToken); // remove after use

        if (tokenSecret == null) {
            log.error("No oauth_token_secret found for token {}", oauthToken);
            return Mono.just(ResponseEntity.badRequest()
                    .body("Authentication failed: Missing token secret. Please restart the authentication process."));
        }

        log.info("Token secret found, exchanging for access token");

        return xAuthService.getAccessToken(oauthToken, oauthVerifier, tokenSecret)
                .doOnNext(accessTokenResponse ->
                        log.info("Successfully received access token for user: {}",
                                accessTokenResponse.get("screen_name")))
                .flatMap(accessTokenResponse -> {
                    String accessToken = accessTokenResponse.get("oauth_token");
                    String accessTokenSecret = accessTokenResponse.get("oauth_token_secret");
                    String screenName = accessTokenResponse.get("screen_name");
                    String userId = accessTokenResponse.get("user_id");

                    return xAuthService.saveCredentials(accessToken, accessTokenSecret, screenName, userId)
                            .doOnNext(saved -> log.info("Saved credentials for user: {} (ID: {})", screenName, userId))
                            .map(saved ->
                                    ResponseEntity.ok(String.format(
                                            "🎉 X Authentication Successful!%n" +
                                                    "Welcome, @%s!%n" +
                                                    "User ID: %s%n" +
                                                    "You can now use X API features.%n%n" +
                                                    "Try: GET /api/status to see your profile data",
                                            screenName, userId
                                    ))
                            );
                })
                .doOnError(error -> {
                    log.error("Detailed error in token exchange: ", error);
                })
                .onErrorReturn(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Authentication failed during token exchange. Please try again.")
                );
    }
}