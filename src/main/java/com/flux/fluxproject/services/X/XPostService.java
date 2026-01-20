package com.flux.fluxproject.services.X;

import com.flux.fluxproject.exceptions.XAccountNotConnectedException;
import com.flux.fluxproject.exceptions.XPostException;
import com.flux.fluxproject.exceptions.XTokenRefreshFailedException;
import com.flux.fluxproject.model.XPostResponse;
import com.flux.fluxproject.model.XTokenResponse;
import com.flux.fluxproject.repositories.SocialAccountRepository;
import com.flux.fluxproject.services.utils.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class XPostService {

    @Qualifier("xWebClient")
    private final WebClient xWebClient;

    private final EncryptionUtil encryptionUtil;

    @Value("${x.client-id}")
    private String clientId;

    @Value("${x.client-secret}")
    private String clientSecret;

    private final SocialAccountRepository socialAccountRepository;

    public Mono<Boolean> checkAccessTokenExpiry(UUID userId) {
        return socialAccountRepository.findByUserIdAndPlatform(userId, "X")  // ← Fixed
                .map(account -> OffsetDateTime.now().isAfter(account.getExpiresAt()))
                .defaultIfEmpty(true);
    }

    public Mono<XPostResponse> postText(String text, String accessToken) {
        return xWebClient.post()
                .uri("/2/tweets")
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .bodyValue(Map.of("text", text))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    log.error("X API error: {}", body);
                                    return Mono.error(new XPostException("X API error: " + body));
                                })
                )
                .bodyToMono(XPostResponse.class)
                .doOnSuccess(response -> log.info("Posted tweet successfully: {}", response.getTweetId()))
                .doOnError(error -> log.error("Failed to post text", error));
    }

    public Mono<XPostResponse> postTextWithAutoRefresh(UUID userId, String text) {
        return checkAccessTokenExpiry(userId)
                .flatMap(isExpired -> {
                    if (isExpired) {
                        log.info("Access token expired for user with ID: {} ... Refreshing", userId);
                        return refreshAccessToken(userId)
                                .onErrorMap(e -> new XTokenRefreshFailedException("Token refresh failed: " + e.getMessage()))
                                .flatMap(tokenResponse ->
                                        saveRefreshedToken(userId, tokenResponse)
                                                .then(getAccessToken(userId))
                                )
                                .flatMap(token -> postText(text, token));
                    } else {
                        return getAccessToken(userId)
                                .flatMap(token -> postText(text, token));
                    }
                })
                .switchIfEmpty(Mono.error(new XAccountNotConnectedException("X account not connected")));
    }

    private Mono<Map<String, Object>> decryptAuthData(String authData) {
        return Mono.fromCallable(() -> {
                    log.debug("Attempting to decrypt auth data");
                    return encryptionUtil.decrypt(authData);
                })
                .onErrorMap(e -> {
                    log.error("Decryption failed. Auth data might be corrupted.", e);
                    return new RuntimeException("Decryption failed: " + e.getMessage(), e);
                });
    }

    private String extractToken(Map<String, Object> data, String key) {
        Object token = data.get(key);
        if (token == null) {
            throw new IllegalStateException("Token '" + key + "' not found in decrypted data");
        }
        return token.toString();
    }

    public Mono<String> getRefreshToken(UUID userId) {
        return socialAccountRepository.findByUserIdAndPlatform(userId, "X")  // ← Fixed
                .switchIfEmpty(Mono.error(
                        new XAccountNotConnectedException("X account not connected for user: " + userId)
                ))
                .map(account -> account.getAuthData())
                .flatMap(this::decryptAuthData)
                .map(data -> extractToken(data, "refresh_token"))
                .doOnError(e -> log.error("Failed to get refresh token for user: {}", userId, e));
    }

    public Mono<String> getAccessToken(UUID userId) {
        return socialAccountRepository.findByUserIdAndPlatform(userId, "X")  // ← Fixed
                .switchIfEmpty(Mono.error(
                        new XAccountNotConnectedException("X account not connected for user: " + userId)
                ))
                .map(account -> account.getAuthData())
                .flatMap(this::decryptAuthData)
                .map(data -> extractToken(data, "access_token"))
                .doOnError(e -> log.error("Failed to get access token for user: {}", userId, e));
    }

    public Mono<XTokenResponse> refreshAccessToken(UUID userId) {
        String credentials = clientId + ":" + clientSecret;
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        return getRefreshToken(userId)
                .flatMap(refreshToken ->
                        xWebClient.post()
                                .uri("/2/oauth2/token")
                                .header("Authorization", "Basic " + encodedCredentials)
                                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                .body(BodyInserters.fromFormData("grant_type", "refresh_token")
                                        .with("refresh_token", refreshToken))
                                .retrieve()
                                .onStatus(HttpStatusCode::isError,
                                        response -> response.bodyToMono(String.class)
                                                .flatMap(body -> {
                                                    log.error("X token refresh failed: {}", body);
                                                    return Mono.error(
                                                            new XTokenRefreshFailedException("Token refresh failed: " + body)
                                                    );
                                                })
                                )
                                .bodyToMono(XTokenResponse.class)
                                .doOnSuccess(resp -> log.info("Successfully refreshed token for userId: {}", userId))
                );
    }

    private Mono<Void> saveRefreshedToken(UUID userId, XTokenResponse response) {
        return socialAccountRepository.findByUserIdAndPlatform(userId, "X")  // ← Fixed
                .switchIfEmpty(Mono.error(
                        new XAccountNotConnectedException("X account not found for user: " + userId)
                ))
                .flatMap(account -> {
                    try {
                        // Create token data map
                        Map<String, Object> data = new HashMap<>();
                        data.put("access_token", response.getAccessToken());
                        data.put("refresh_token", response.getRefreshToken());
                        data.put("expires_in", response.getExpiresIn());
                        data.put("scope", response.getScope());

                        // Encrypt the data
                        String encryptedData = encryptionUtil.encrypt(data);

                        // Update account
                        account.setAuthData(encryptedData);
                        account.setExpiresAt(calculateExpiryTime(response.getExpiresIn()));
                        account.setIsActive(true);

                        return socialAccountRepository.save(account);
                    } catch (Exception e) {
                        log.error("Failed to encrypt token data for userId: {}", userId, e);
                        return Mono.error(new RuntimeException("Failed to encrypt token data", e));
                    }
                })
                .then()
                .doOnSuccess(v -> log.info("Saved refreshed token for user with ID: {}", userId))
                .doOnError(e -> log.error("Failed to save token for user with ID: {}", userId, e));
    }

    private OffsetDateTime calculateExpiryTime(Long expiresIn) {
        if (expiresIn == null) {
            return OffsetDateTime.now().plusDays(60);
        }
        return OffsetDateTime.now().plusSeconds(expiresIn);
    }
}

// Even better: Generic method to avoid duplication
//    private Mono<String> getTokenByType(UUID userId, String tokenType) {
//        return socialAccountRepository.findAuthDataByUserId(userId)
//                .switchIfEmpty(Mono.error(
//                        new IllegalStateException("No auth data for user: " + userId)
//                ))
//                .flatMap(this::decryptAuthData)
//                .map(data -> extractToken(data, tokenType))
//                .doOnError(e -> log.error("Failed to get {} for user: {}", tokenType, userId, e));
//    }

//    public Mono<String> getRefreshToken(UUID userId) {
//        return getTokenByType(userId, "refresh_token");
//    }
//
//    public Mono<String> getAccessToken(UUID userId) {
//        return getTokenByType(userId, "access_token");
//    }
