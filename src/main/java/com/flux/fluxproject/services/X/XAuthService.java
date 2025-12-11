package com.flux.fluxproject.services.X;

import com.flux.fluxproject.domain.OAuthState;
import com.flux.fluxproject.mappers.SocialAccountMapper;
import com.flux.fluxproject.mappers.UserMapper;
import com.flux.fluxproject.model.SocialAccountDTO;
import com.flux.fluxproject.model.UserDTO;
import com.flux.fluxproject.repositories.OAuthStateRepository;
import com.flux.fluxproject.repositories.SocialAccountRepository;
import com.flux.fluxproject.repositories.UserRepository;
import com.flux.fluxproject.services.utils.EncryptionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class XAuthService {

    private final WebClient webClient;
    private final OAuth1Signer signer;
    private final SocialAccountRepository socialAccountRepository;
    private final SocialAccountMapper socialAccountMapper;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final EncryptionUtil encryptionUtil;
    private final OAuthStateRepository oauthStateRepository;

    private static final String REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
    private static final String ACCESS_TOKEN_URL = "https://api.twitter.com/oauth/access_token";
    private static final String AUTHORIZE_URL = "https://api.twitter.com/oauth/authorize";
    private static final String VERIFY_CREDENTIALS_URL = "https://api.twitter.com/1.1/account/verify_credentials.json";

    public XAuthService(WebClient.Builder builder,
                        @Value("${x.api-key}") String consumerKey,
                        @Value("${x.api-secret}") String consumerSecret,
                        SocialAccountRepository socialAccountRepository,
                        SocialAccountMapper socialAccountMapper,
                        UserRepository userRepository,
                        UserMapper userMapper,
                        EncryptionUtil encryptionUtil,
                        OAuthStateRepository oauthStateRepository) {
        this.webClient = builder.build();
        this.socialAccountRepository = socialAccountRepository;
        this.socialAccountMapper = socialAccountMapper;
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.encryptionUtil = encryptionUtil;
        this.oauthStateRepository = oauthStateRepository;
        this.signer = new OAuth1Signer(consumerKey, consumerSecret);

        log.info("XAuthService initialized");
        log.info("Consumer key length: {}", consumerKey != null ? consumerKey.length() : 0);
    }

    /**
     * Save OAuth state to database
     * This is called when the OAuth flow starts (user clicks "Connect X")
     */
    public Mono<OAuthState> saveOAuthState(String userId, String oauthToken, String tokenSecret) {
        log.info("=== SAVING OAUTH STATE ===");
        log.info("User ID: {}", userId);
        log.info("OAuth Token: {}", oauthToken);

        OAuthState state = new OAuthState();
        state.setUserId(UUID.fromString(userId));
        state.setPlatform("twitter");
        state.setOauthToken(oauthToken);
        state.setTokenSecret(tokenSecret);
        state.setCreatedAt(Instant.now());
        state.setExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES)); // 10 min expiry
        state.setConsumed(false);

        return oauthStateRepository.save(state)
                .doOnSuccess(saved -> {
                    log.info("✅ OAuth state saved successfully");
                    log.info("   State ID: {}", saved.getId());
                    log.info("   Expires at: {}", saved.getExpiresAt());
                })
                .doOnError(error -> {
                    log.error("❌ Failed to save OAuth state", error);
                });
    }

    /**
     * Retrieve OAuth state from database
     * This is called when user returns from X (callback)
     */
    public Mono<OAuthState> getOAuthState(String oauthToken) {
        log.info("=== RETRIEVING OAUTH STATE ===");
        log.info("OAuth Token: {}", oauthToken);

        return oauthStateRepository.findByOauthTokenAndConsumedFalseAndExpiresAtAfter(
                        oauthToken,
                        Instant.now()
                )
                .doOnSuccess(state -> {
                    if (state != null) {
                        log.info("✅ Valid OAuth state found");
                        log.info("   State ID: {}", state.getId());
                        log.info("   User ID: {}", state.getUserId());
                        log.info("   Platform: {}", state.getPlatform());
                        log.info("   Created: {}", state.getCreatedAt());
                        log.info("   Expires: {}", state.getExpiresAt());
                        log.info("   Consumed: {}", state.getConsumed());
                    } else {
                        log.warn("❌ No valid OAuth state found");
                        log.warn("   Token may be expired, consumed, or invalid");
                    }
                })
                .doOnError(error -> {
                    log.error("❌ Error retrieving OAuth state", error);
                });
    }

    /**
     * Mark OAuth state as consumed after successful callback
     * Prevents the same OAuth token from being used twice
     */
    public Mono<Void> consumeOAuthState(String oauthToken) {
        log.info("=== CONSUMING OAUTH STATE ===");
        log.info("OAuth Token: {}", oauthToken);

        return oauthStateRepository.findByOauthToken(oauthToken)
                .flatMap(state -> {
                    state.setConsumed(true);
                    return oauthStateRepository.save(state);
                })
                .doOnSuccess(state -> {
                    log.info("✅ OAuth state marked as consumed");
                    log.info("   State ID: {}", state.getId());
                })
                .doOnError(error -> {
                    log.error("❌ Failed to consume OAuth state", error);
                })
                .then();
    }

    /**
     * Get request token from X (Step 1 of OAuth 1.0a)
     */
    public Mono<Map<String, String>> getRequestToken(String callbackUrl) {
        log.info("=== REQUESTING TOKEN FROM X ===");
        log.info("Callback URL: {}", callbackUrl);

        try {
            Map<String, String> params = signer.buildOAuthParams(callbackUrl, null);
            String signature = signer.sign("POST", REQUEST_TOKEN_URL, params, null);
            params.put("oauth_signature", signature);
            String authHeader = signer.buildAuthHeader(params);

            return webClient.post()
                    .uri(REQUEST_TOKEN_URL)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(this::parseQueryString)
                    .doOnSuccess(response -> {
                        log.info("✅ Request token received from X");
                        log.info("   OAuth Token: {}", response.get("oauth_token"));
                    })
                    .doOnError(error -> {
                        log.error("❌ Failed to get request token from X", error);
                    });

        } catch (Exception e) {
            log.error("❌ Exception in getRequestToken", e);
            return Mono.error(e);
        }
    }

    /**
     * Build authorization URL for user to visit
     */
    public String buildAuthorizeUrl(String oauthToken) {
        return AUTHORIZE_URL + "?oauth_token=" + oauthToken;
    }

    /**
     * Exchange request token for access token (Step 3 of OAuth 1.0a)
     */
    public Mono<Map<String, String>> getAccessToken(String oauthToken, String oauthVerifier, String tokenSecret) {
        log.info("=== EXCHANGING FOR ACCESS TOKEN ===");
        log.info("OAuth Token: {}", oauthToken);
        log.info("OAuth Verifier: {}", oauthVerifier);

        try {
            Map<String, String> params = signer.buildOAuthParams(null, oauthToken);
            params.put("oauth_verifier", oauthVerifier);
            String signature = signer.sign("POST", ACCESS_TOKEN_URL, params, tokenSecret);
            params.put("oauth_signature", signature);
            String authHeader = signer.buildAuthHeader(params);

            return webClient.post()
                    .uri(ACCESS_TOKEN_URL)
                    .header(HttpHeaders.AUTHORIZATION, authHeader)
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(this::parseQueryString)
                    .doOnSuccess(response -> {
                        log.info("✅ Access token received from X");
                        log.info("   Screen Name: @{}", response.get("screen_name"));
                        log.info("   User ID: {}", response.get("user_id"));
                    })
                    .doOnError(error -> {
                        log.error("❌ Failed to get access token from X", error);
                    });

        } catch (Exception e) {
            log.error("❌ Exception in getAccessToken", e);
            return Mono.error(e);
        }
    }

    /**
     * Parse X's response (form-encoded)
     */
    private Map<String, String> parseQueryString(String body) {
        if (body == null || body.trim().isEmpty()) {
            return Map.of();
        }
        return Arrays.stream(body.split("&"))
                .map(kv -> kv.split("=", 2))
                .filter(kv -> kv.length == 2)
                .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));
    }

    /**
     * Save X credentials to database
     * Links the X account to the logged-in user
     *
     * @param accessToken X access token
     * @param accessTokenSecret X access token secret
     * @param screenName X username (e.g., @elonmusk)
     * @param platformUserId X's internal user ID
     * @param userIdStr Our application's user ID (from JWT)
     */
    public Mono<SocialAccountDTO> saveCredentials(String accessToken,
                                                  String accessTokenSecret,
                                                  String screenName,
                                                  String platformUserId,
                                                  String userIdStr) {

        log.info("==========================================================");
        log.info("SAVING X CREDENTIALS");
        log.info("Platform User ID (X's ID): {}", platformUserId);
        log.info("Screen Name: @{}", screenName);
        log.info("Our User ID (from JWT): {}", userIdStr);
        log.info("==========================================================");

        try {
            // Encrypt the sensitive auth data
            Map<String, Object> authData = new HashMap<>();
            authData.put("access_token", accessToken);
            authData.put("access_token_secret", accessTokenSecret);
            authData.put("auth_method", "oauth");
            String encrypted = encryptionUtil.encrypt(authData);

            // Check if this X account is already linked to ANY user
            return socialAccountRepository.findFirstByPlatformAndPlatformUserId("twitter", platformUserId)
                    .flatMap(existingSocialAccount -> {
                        // X ACCOUNT ALREADY EXISTS - UPDATE IT
                        log.info("✅ This X account is already linked");
                        log.info("   Existing Social Account ID: {}", existingSocialAccount.getId());
                        log.info("   Currently linked to User ID: {}", existingSocialAccount.getUserId());
                        log.info("   Requested by User ID: {}", userIdStr);

                        // Check if it's the same user
                        if (existingSocialAccount.getUserId().toString().equals(userIdStr)) {
                            log.info("✅ Same user - updating credentials");
                        } else {
                            log.warn("⚠️  Different user - re-linking X account to new user");
                            existingSocialAccount.setUserId(UUID.fromString(userIdStr));
                        }

                        existingSocialAccount.setUsername(screenName);
                        existingSocialAccount.setAuthData(encrypted);
                        existingSocialAccount.setIsActive(true);
                        existingSocialAccount.setUpdatedAt(OffsetDateTime.now());

                        return socialAccountRepository.save(existingSocialAccount)
                                .doOnSuccess(saved -> {
                                    log.info("✅✅ SOCIAL ACCOUNT UPDATED");
                                    log.info("   Social Account ID: {}", saved.getId());
                                    log.info("   Linked to User ID: {}", saved.getUserId());
                                    log.info("==========================================================");
                                })
                                .map(socialAccountMapper::socialAccountToSocialAccountDto);
                    })
                    .switchIfEmpty(
                            // X ACCOUNT NOT FOUND - CREATE NEW LINK
                            Mono.defer(() -> {
                                log.info("❌ This X account has never been linked before");
                                log.info("✅ Creating new social account link to User ID: {}", userIdStr);

                                SocialAccountDTO socialAccountDto = SocialAccountDTO.builder()
                                        .userId(UUID.fromString(userIdStr))
                                        .platform("twitter")
                                        .platformUserId(platformUserId)
                                        .username(screenName)
                                        .authData(encrypted)
                                        .isActive(true)
                                        .build();

                                return socialAccountRepository.save(
                                                socialAccountMapper.socialAccountDtoToSocialAccount(socialAccountDto)
                                        )
                                        .doOnSuccess(saved -> {
                                            log.info("✅✅ NEW SOCIAL ACCOUNT CREATED");
                                            log.info("   Social Account ID: {}", saved.getId());
                                            log.info("   X Username: @{}", saved.getUsername());
                                            log.info("   Linked to User ID: {}", saved.getUserId());
                                            log.info("==========================================================");
                                        })
                                        .map(socialAccountMapper::socialAccountToSocialAccountDto);
                            })
                    );

        } catch (Exception e) {
            log.error("❌ Encryption failed while saving credentials", e);
            log.error("==========================================================");
            return Mono.error(e);
        }
    }

    /**
     * Create a new user from social account signup
     * This is used if someone signs up via X without an existing account
     * (Currently not used since we require login first, but kept for future use)
     */
    private Mono<UserDTO> createNewUserFromSocialAccount(String screenName, String platformUserId) {
        log.info("=== CREATING NEW USER FROM X ACCOUNT ===");
        log.info("Screen Name: @{}", screenName);
        log.info("Platform User ID: {}", platformUserId);

        UserDTO newUser = UserDTO.builder()
                .name(screenName)
                .email(null) // X OAuth doesn't provide email
                .build();

        return userRepository.save(userMapper.userDtoToUser(newUser))
                .doOnNext(savedUser -> {
                    log.info("✅ New user created");
                    log.info("   User ID: {}", savedUser.getId());
                    log.info("   Name: {}", savedUser.getName());
                })
                .doOnError(error -> {
                    log.error("❌ Failed to create user from X account", error);
                })
                .map(userMapper::userToUserDto);
    }
}