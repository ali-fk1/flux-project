    package com.flux.fluxproject.services.X;

    import com.flux.fluxproject.mappers.SocialAccountMapper;
    import com.flux.fluxproject.mappers.UserMapper;
    import com.flux.fluxproject.model.SocialAccountDTO;
    import com.flux.fluxproject.model.UserDTO;
    import com.flux.fluxproject.repositories.SocialAccountRepository;
    import com.flux.fluxproject.repositories.UserRepository;
    import com.flux.fluxproject.services.utils.EncryptionUtil;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.http.HttpHeaders;
    import org.springframework.stereotype.Service;
    import org.springframework.web.reactive.function.client.WebClient;
    import reactor.core.publisher.Mono;

    import java.util.Arrays;
    import java.util.HashMap;
    import java.util.Map;
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

        private static final String REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
        private static final String ACCESS_TOKEN_URL = "https://api.twitter.com/oauth/access_token";
        private static final String AUTHORIZE_URL = "https://api.twitter.com/oauth/authorize";
        private static final String VERIFY_CREDENTIALS_URL = "https://api.twitter.com/1.1/account/verify_credentials.json";
    
        public XAuthService(WebClient.Builder builder,
                            @Value("${x.api-key}") String consumerKey,
                            @Value("${x.api-secret}") String consumerSecret, SocialAccountRepository socialAccountRepository, SocialAccountMapper socialAccountMapper, UserRepository userRepository, UserMapper userMapper, EncryptionUtil encryptionUtil) {
            this.webClient = builder.build();
            this.socialAccountRepository = socialAccountRepository;
            this.socialAccountMapper = socialAccountMapper;
            this.userRepository = userRepository;
            this.userMapper = userMapper;
            this.encryptionUtil = encryptionUtil;
            this.signer = new OAuth1Signer(consumerKey, consumerSecret);
    
            // Log configuration (safely)
            log.info("XAuthService initialized");
            log.info("Consumer key length: {}", consumerKey != null ? consumerKey.length() : 0);
            log.info("Consumer secret length: {}", consumerSecret != null ? consumerSecret.length() : 0);
            log.info("Consumer key starts with: {}", consumerKey != null && consumerKey.length() > 5 ?
                    consumerKey.substring(0, 5) + "..." : "NULL");
        }
    
        // Step 1: Request token with detailed logging
        public Mono<Map<String, String>> getRequestToken(String callbackUrl) {
            log.info("=== Starting OAuth Request Token Process ===");
            log.info("Callback URL: {}", callbackUrl);
    
            try {
                // Build OAuth parameters
                Map<String, String> params = signer.buildOAuthParams(callbackUrl, null);
                log.info("OAuth parameters before signature: {}", params);
    
                // Generate signature
                String signature = signer.sign("POST", REQUEST_TOKEN_URL, params, null);
                params.put("oauth_signature", signature);
                log.info("Generated signature: {}", signature);
    
                // Build authorization header
                String authHeader = signer.buildAuthHeader(params);
                log.info("Authorization header: {}", authHeader);
    
                // Make the request
                log.info("Making POST request to: {}", REQUEST_TOKEN_URL);
    
                return webClient.post()
                        .uri(REQUEST_TOKEN_URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .retrieve()
                        .bodyToMono(String.class)
                        .doOnNext(response -> log.info("Raw response from X: {}", response))
                        .map(this::parseQueryString)
                        .doOnNext(parsed -> log.info("Parsed response: {}", parsed))
                        .doOnError(error -> log.error("Request failed: ", error));
    
            } catch (Exception e) {
                log.error("Error in getRequestToken: ", e);
                return Mono.error(e);
            }
        }
    
        // Step 2: Build redirect URL for user authorization
        public String buildAuthorizeUrl(String oauthToken) {
            String url = AUTHORIZE_URL + "?oauth_token=" + oauthToken;
            log.info("Built authorize URL: {}", url);
            return url;
        }
    
        // Step 3: Exchange request token for access token
        public Mono<Map<String, String>> getAccessToken(String oauthToken, String oauthVerifier, String tokenSecret) {
            log.info("=== Starting Access Token Exchange ===");
            log.info("OAuth token: {}", oauthToken);
            log.info("OAuth verifier: {}", oauthVerifier);
            log.info("Token secret length: {}", tokenSecret != null ? tokenSecret.length() : 0);
    
            try {
                Map<String, String> params = signer.buildOAuthParams(null, oauthToken);
                params.put("oauth_verifier", oauthVerifier);
                log.info("Parameters before signature: {}", params);
    
                String signature = signer.sign("POST", ACCESS_TOKEN_URL, params, tokenSecret);
                params.put("oauth_signature", signature);
                log.info("Generated signature: {}", signature);
    
                String authHeader = signer.buildAuthHeader(params);
                log.info("Authorization header: {}", authHeader);
    
                return webClient.post()
                        .uri(ACCESS_TOKEN_URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .retrieve()
                        .bodyToMono(String.class)
                        .doOnNext(response -> log.info("Access token response: {}", response))
                        .map(this::parseQueryString)
                        .doOnNext(parsed -> log.info("Parsed access token: {}", parsed));
    
            } catch (Exception e) {
                log.error("Error in getAccessToken: ", e);
                return Mono.error(e);
            }
        }
    
        // Test API call with access token
        public Mono<String> getUserProfile(String accessToken, String accessTokenSecret) {
            log.info("=== Fetching User Profile ===");
            log.info("Access token length: {}", accessToken != null ? accessToken.length() : 0);
    
            try {
                Map<String, String> params = signer.buildOAuthParams(null, accessToken);
                String signature = signer.sign("GET", VERIFY_CREDENTIALS_URL, params, accessTokenSecret);
                params.put("oauth_signature", signature);
                String authHeader = signer.buildAuthHeader(params);
    
                log.info("Profile request auth header: {}", authHeader);
    
                return webClient.get()
                        .uri(VERIFY_CREDENTIALS_URL)
                        .header(HttpHeaders.AUTHORIZATION, authHeader)
                        .retrieve()
                        .bodyToMono(String.class)
                        .doOnNext(profile -> log.info("Profile response received (length: {})", profile.length()));
    
            } catch (Exception e) {
                log.error("Error in getUserProfile: ", e);
                return Mono.error(e);
            }
        }
    
        // helper: parse responses like "oauth_token=...&oauth_token_secret=..."
        private Map<String, String> parseQueryString(String body) {
            if (body == null || body.trim().isEmpty()) {
                log.warn("Empty response body received");
                return Map.of();
            }

            Map<String, String> result = Arrays.stream(body.split("&"))
                    .map(kv -> kv.split("=", 2))
                    .filter(kv -> kv.length == 2)
                    .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));

            log.info("Parsed query string: {} -> {}", body, result);
            return result;
        }

        public Mono<SocialAccountDTO> saveCredentials(String accessToken,
                                                      String accessTokenSecret,
                                                      String screenName,
                                                      String userId) {

            return createOrFindUserForSocialAccount(screenName, userId)
                    .flatMap(user -> {
                        try {
                            Map<String, Object> authData = new HashMap<>();
                            authData.put("access_token", accessToken);
                            authData.put("access_token_secret", accessTokenSecret);
                            authData.put("auth_method", "oauth");

                            // 🔒 Encrypt before storing
                            String encrypted = encryptionUtil.encrypt(authData);

                            SocialAccountDTO socialAccountDto = SocialAccountDTO.builder()
                                    .userId(user.getId())
                                    .platform("twitter")
                                    .platformUserId(userId)
                                    .username(screenName)
                                    .authData(encrypted) // store ciphertext
                                    .isActive(true)
                                    .build();
                            Map<String, Object> decryptedMap = encryptionUtil.decrypt(encrypted);

                            log.info("===== Decrypted Map =====");
                            decryptedMap.forEach((key, value) ->
                                    log.info("Key: {}, Value: {}", key, value)
                            );
                            log.info("=========================");
                            return socialAccountRepository.save(
                                    socialAccountMapper.socialAccountDtoToSocialAccount(socialAccountDto)
                            ).map(socialAccountMapper::socialAccountToSocialAccountDto);

                        } catch (Exception e) {
                            log.error("Encryption failed while saving credentials", e);
                            return Mono.error(e);
                        }
                    });
        }

        // //////////////////////////////////
        // those are temporary methods for development purposes
        // //////////////////////////////////
        private Mono<UserDTO> createOrFindUserForSocialAccount(String screenName, String platformUserId) {
            log.info("=== createOrFindUserForSocialAccount called with screenName: {}, platformUserId: {}", screenName, platformUserId);

            return socialAccountRepository.findByPlatformAndPlatformUserId("twitter", platformUserId)
                    .doOnNext(existing -> log.info("Found existing social account: {}", existing.getId()))
                    .map(socialAccount -> socialAccount.getUserId())
                    .flatMap(userId -> {
                        log.info("Found existing user ID: {}, fetching user details", userId);
                        return userRepository.findById(userId);
                    })
                    .map(userMapper::userToUserDto)
                    .doOnNext(existingUser -> log.info("Found existing user: {}", existingUser.getId()))
                    .switchIfEmpty(
                            Mono.defer(() -> {
                                log.info("No existing user found, creating new user");
                                return createNewUserFromSocialAccount(screenName, platformUserId);
                            })
                    );
        }

        private Mono<UserDTO> createNewUserFromSocialAccount(String screenName, String platformUserId) {
            log.info("=== Creating new user for screenName: {}", screenName);

            UserDTO newUser = UserDTO.builder()
                    .name(screenName)
                    .email(null)
                    .build();

            log.info("New user DTO created with ID: {}", newUser.getId());

            return userRepository.save(userMapper.userDtoToUser(newUser))
                    .doOnNext(savedUser -> log.info("User saved successfully with ID: {}", savedUser.getId()))
                    .doOnError(error -> log.error("CRITICAL: Failed to save user: ", error))
                    .map(userMapper::userToUserDto)
                    .doOnNext(userDto -> log.info("Returning UserDTO with ID: {}", userDto.getId()));
        }
    }