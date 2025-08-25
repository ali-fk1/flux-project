package com.flux.fluxproject.controllers;

import com.flux.fluxproject.services.X.XAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@RequestMapping( "/api" )
@Slf4j
public class PlatformController {

    private final XAuthService  xAuthService;

    @Value("${x.callback-url}")
    public String callbackUrl;


    /**
     * Step 1: Start X OAuth authentication flow
     * POST /api/x/auth/start
     *
     * This endpoint:
     * 1. Gets a request token from X
     * 2. Stores the token secret in session
     * 3. Redirects user to X authorization page
     */
    @PostMapping("/x")
    public Mono<ResponseEntity<String>> startXAuthFlow(WebSession session) {
        log.info("Starting X OAuth flow with callback URL: {}", callbackUrl);

        return xAuthService.getRequestToken(callbackUrl)
                .doOnNext(response -> log.info("Received request token: {}", response.get("oauth_token")))
                .map(response -> {
                    // Extract tokens from response
                    String oauthToken = response.get("oauth_token");
                    String oauthTokenSecret = response.get("oauth_token_secret");

                    // Store token secret in session for later use
                    session.getAttributes().put("oauth_token_secret", oauthTokenSecret);
                    session.getAttributes().put("oauth_token", oauthToken);

                    log.info("Stored token secret in session for token: {}", oauthToken);

                    // Build X authorization URL
                    String authorizeUrl = xAuthService.buildAuthorizeUrl(oauthToken);
                    log.info("Redirecting user to X authorization URL: {}", authorizeUrl);

                    // Return redirect response to X
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .location(URI.create(authorizeUrl))
                            .body("Redirecting to X for authorization... If not redirected automatically, click here: " + authorizeUrl);
                })
                .doOnError(error -> log.error("Error starting X OAuth flow: ", error))
                .onErrorReturn(
                        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body("Failed to start X authentication. Please try again.")
                );
    }

    /**
     * Step 2: Handle callback from X after user authorization
     * GET /api/x/auth/callback?oauth_token=...&oauth_verifier=...
     *
     * This endpoint:
     * 1. Receives oauth_token and oauth_verifier from X
     * 2. Retrieves token secret from session
     * 3. Exchanges request token for access token
     * 4. Stores access token in session
     */
    @GetMapping("/x/callback")
    public Mono<ResponseEntity<String>> handleXCallback(
                @RequestParam("oauth_token") String oauthToken,
                @RequestParam("oauth_verifier") String oauthVerifier,
                WebSession session) {

            log.info("Handling X OAuth callback - Token: {}, Verifier: {}", oauthToken, oauthVerifier);

            // Get stored token secret from session
            String tokenSecret = session.getAttribute("oauth_token_secret");
            String sessionToken = session.getAttribute("oauth_token");

            // Validate session data
            if (tokenSecret == null) {
                log.error("No oauth_token_secret found in session");
                return Mono.just(ResponseEntity.badRequest()
                        .body("Authentication failed: Missing token secret. Please restart the authentication process."));
            }

            if (!oauthToken.equals(sessionToken)) {
                log.error("OAuth token mismatch. Session: {}, Callback: {}", sessionToken, oauthToken);
                return Mono.just(ResponseEntity.badRequest()
                        .body("Authentication failed: Token mismatch. Please restart the authentication process."));
            }

            log.info("Session validation successful, exchanging for access token");

            // Exchange request token for access token
            return xAuthService.getAccessToken(oauthToken, oauthVerifier, tokenSecret)
                    .doOnNext(accessTokenResponse ->
                            log.info("Successfully received access token for user: {}",
                                    accessTokenResponse.get("screen_name")))
                    .map(accessTokenResponse -> {
                        // Extract access token data
                        String accessToken = accessTokenResponse.get("oauth_token");
                        String accessTokenSecret = accessTokenResponse.get("oauth_token_secret");
                        String screenName = accessTokenResponse.get("screen_name");
                        String userId = accessTokenResponse.get("user_id");

                        // Store access token in session
                        session.getAttributes().put("x_access_token", accessToken);
                        session.getAttributes().put("x_access_token_secret", accessTokenSecret);
                        session.getAttributes().put("x_screen_name", screenName);
                        session.getAttributes().put("x_user_id", userId);

                        // Clean up temporary session data
                        session.getAttributes().remove("oauth_token_secret");
                        session.getAttributes().remove("oauth_token");

                        log.info("X OAuth authentication completed for user: {} (ID: {})", screenName, userId);

                        // Return success response
                        return ResponseEntity.ok(String.format(
                                "🎉 X Authentication Successful!%n" +
                                        "Welcome, @%s!%n" +
                                        "User ID: %s%n" +
                                        "You can now use X API features.%n%n" +
                                        "Try: GET /api/x/profile to see your profile data",
                                screenName, userId
                        ));
                    })
                    .doOnError(error -> log.error("Error exchanging tokens: ", error))
                    .onErrorReturn(
                            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body("Authentication failed during token exchange. Please try again.")
                    );

    }



    /**
     * Test endpoint to verify authentication status
     * GET /api/x/status
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<String>> getAuthStatus(WebSession session) {
        String accessToken = session.getAttribute("x_access_token");
        String screenName = session.getAttribute("x_screen_name");
        String userId = session.getAttribute("x_user_id");

        if (accessToken == null) {
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("❌ Not authenticated with X. Start authentication: POST /api/x"));
        }

        return Mono.just(ResponseEntity.ok(String.format(
                "✅ Authenticated with X%n" +
                        "Screen Name: @%s%n" +
                        "User ID: %s%n" +
                        "Access Token: %s...%n" +
                        "Ready to make X API calls!",
                screenName, userId, accessToken.substring(0, 10)
        )));
    }





}
