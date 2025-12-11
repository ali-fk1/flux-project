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


@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class PlatformController {

    private final XAuthService xAuthService;

    @Value("${x.callback-url}")
    public String callbackUrl;

    /**
     * Step 1: Start X OAuth authentication flow
     * POST /api/x
     *
     * This endpoint requires authentication - the JWT filter extracts userId
     * and makes it available via @RequestAttribute
     */
    @PostMapping("/x")
    public Mono<ResponseEntity<Map<String, String>>> startXAuthFlow(
            @RequestAttribute("userId") String userId) {

        log.info("============================================================");
        log.info("STARTING X OAUTH FLOW");
        log.info("User ID from JWT: {}", userId);
        log.info("Callback URL: {}", callbackUrl);
        log.info("============================================================");

        return xAuthService.getRequestToken(callbackUrl)
                .doOnNext(response -> log.info("✅ Received request token: {}", response.get("oauth_token")))
                .flatMap(response -> {
                    String oauthToken = response.get("oauth_token");
                    String oauthTokenSecret = response.get("oauth_token_secret");

                    log.info("Saving OAuth state to database...");

                    // Store OAuth state in database with logged-in user's ID
                    return xAuthService.saveOAuthState(userId, oauthToken, oauthTokenSecret)
                            .map(savedState -> {
                                log.info("✅ OAuth state saved with ID: {}", savedState.getId());
                                log.info("   User ID: {}", savedState.getUserId());
                                log.info("   OAuth Token: {}", savedState.getOauthToken());
                                log.info("   Expires At: {}", savedState.getExpiresAt());

                                String authorizeUrl = xAuthService.buildAuthorizeUrl(oauthToken);
                                log.info("✅ Authorization URL created: {}", authorizeUrl);
                                log.info("============================================================");

                                Map<String, String> responseBody = new HashMap<>();
                                responseBody.put("authorizeUrl", authorizeUrl);
                                responseBody.put("message", "Please visit the authorization URL to connect your X account");

                                return ResponseEntity.ok(responseBody);
                            });
                })
                .doOnError(error -> {
                    log.error("============================================================");
                    log.error("❌ ERROR STARTING X OAUTH FLOW");
                    log.error("User ID: {}", userId);
                    log.error("Error: ", error);
                    log.error("============================================================");
                })
                .onErrorReturn(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("error", "Failed to start X authentication. Please try again."))
                );
    }

    /**
     * Step 2: Handle callback from X after user authorization
     * GET /api/x/callback?oauth_token=...&oauth_verifier=...
     *
     * NOTE: This endpoint is PUBLIC (listed in JwtAuthenticationFilter.PUBLIC_PATHS)
     * because X redirects the user here directly without any JWT token.
     *
     * We retrieve the userId from the OAuth state we stored in step 1.
     */
    @GetMapping("/x/callback")
    public Mono<ResponseEntity<String>> handleXCallback(
            @RequestParam("oauth_token") String oauthToken,
            @RequestParam("oauth_verifier") String oauthVerifier) {

        log.info("============================================================");
        log.info("X OAUTH CALLBACK RECEIVED");
        log.info("OAuth Token: {}", oauthToken);
        log.info("OAuth Verifier: {}", oauthVerifier);
        log.info("============================================================");

        return xAuthService.getOAuthState(oauthToken)
                .switchIfEmpty(Mono.error(new RuntimeException("OAuth state not found for token: " + oauthToken)))
                .flatMap(oauthState -> {
                    String tokenSecret = oauthState.getTokenSecret();
                    String userId = oauthState.getUserId().toString();

                    log.info("✅ OAuth state found!");
                    log.info("   State ID: {}", oauthState.getId());
                    log.info("   User ID: {}", userId);
                    log.info("   Platform: {}", oauthState.getPlatform());
                    log.info("   Created At: {}", oauthState.getCreatedAt());
                    log.info("   Consumed: {}", oauthState.getConsumed());
                    log.info("Exchanging request token for access token...");

                    return xAuthService.getAccessToken(oauthToken, oauthVerifier, tokenSecret)
                            .doOnNext(accessTokenResponse ->
                                    log.info("✅ Access token received for X user: {}",
                                            accessTokenResponse.get("screen_name")))
                            .flatMap(accessTokenResponse -> {
                                String accessToken = accessTokenResponse.get("oauth_token");
                                String accessTokenSecret = accessTokenResponse.get("oauth_token_secret");
                                String screenName = accessTokenResponse.get("screen_name");
                                String platformUserId = accessTokenResponse.get("user_id");

                                log.info("Access Token Info:");
                                log.info("   Screen Name: @{}", screenName);
                                log.info("   Platform User ID: {}", platformUserId);
                                log.info("Marking OAuth state as consumed...");

                                // Mark OAuth state as consumed, then save credentials
                                return xAuthService.consumeOAuthState(oauthToken)
                                        .doOnSuccess(v -> log.info("✅ OAuth state marked as consumed"))
                                        .then(xAuthService.saveCredentials(
                                                accessToken,
                                                accessTokenSecret,
                                                screenName,
                                                platformUserId,
                                                userId
                                        ))
                                        .doOnNext(saved -> {
                                            log.info("============================================================");
                                            log.info("✅✅ X AUTHENTICATION SUCCESSFUL!");
                                            log.info("   User ID: {}", userId);
                                            log.info("   X Username: @{}", screenName);
                                            log.info("   X User ID: {}", platformUserId);
                                            log.info("   Social Account ID: {}", saved.getId());
                                            log.info("============================================================");
                                        })
                                        .map(saved ->
                                                ResponseEntity.ok(String.format(
                                                        "🎉 X Authentication Successful!%n%n" +
                                                                "Welcome, @%s!%n" +
                                                                "X User ID: %s%n%n" +
                                                                "Your X account has been successfully linked.%n" +
                                                                "You can now close this window and return to the application.%n%n" +
                                                                "You can now use X API features in your dashboard.",
                                                        screenName, platformUserId
                                                ))
                                        );
                            })
                            .doOnError(error -> {
                                log.error("============================================================");
                                log.error("❌ ERROR DURING TOKEN EXCHANGE");
                                log.error("OAuth Token: {}", oauthToken);
                                log.error("User ID: {}", userId);
                                log.error("Error: ", error);
                                log.error("============================================================");
                            })
                            .onErrorReturn(
                                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                            .body("❌ Authentication failed during token exchange.\n\n" +
                                                    "Please try again or contact support if the problem persists.")
                            );
                })
                .onErrorResume(throwable -> {
                    log.error("============================================================");
                    log.error("❌ OAUTH STATE NOT FOUND OR OTHER ERROR");
                    log.error("OAuth Token: {}", oauthToken);
                    log.error("Error: ", throwable);
                    log.error("============================================================");

                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("❌ Authentication failed: Invalid or expired OAuth token.\n\n" +
                                    "Please restart the authentication process from your application."));
                });
    }
}