package com.flux.fluxproject.services.X;

import com.flux.fluxproject.domain.OAuth2AuthRequest;
import com.flux.fluxproject.domain.SocialAccount;
import com.flux.fluxproject.model.XTokenResponse;
import com.flux.fluxproject.model.XUserInfo;
import com.flux.fluxproject.repositories.OAuth2AuthRequestRepository;
import com.flux.fluxproject.repositories.SocialAccountRepository;
import com.flux.fluxproject.services.utils.EncryptionUtil;
import com.flux.fluxproject.services.utils.OAuth2PKCEUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class XOAuth2Service {

    @Qualifier("xWebClient")
    private final WebClient xWebClient;

    @Value("${x.client-id}")
    private String clientId;

    @Value("${x.client-secret}")
    private String clientSecret;

    @Value("${x.redirect-uri}")
    private String redirectUri;

    @Value("${x.code-verifier-length}")
    private int length;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private final EncryptionUtil encryptionUtil;
    private final SocialAccountRepository socialAccountRepository;
    private final OAuth2AuthRequestRepository oAuth2AuthRequestRepository;

    public Mono<String> buildAuthorizationUrl(ServerWebExchange serverWebExchange) {
        try {
            String codeVerifier = OAuth2PKCEUtil.generateCodeVerifier(length);
            String codeChallenge = OAuth2PKCEUtil.generateCodeChallenge(codeVerifier);
            String state = OAuth2PKCEUtil.generateState();

            String userIdStr = serverWebExchange.getAttribute("userId");
            if (userIdStr == null) {
                return Mono.error(new IllegalStateException("Unauthorized: userId missing"));
            }

            UUID userId = UUID.fromString(userIdStr);

            OAuth2AuthRequest oauth2AuthRequest = OAuth2AuthRequest.builder()
                    .userId(userId)
                    .provider("X")
                    .codeVerifier(codeVerifier)
                    .state(state)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(600))
                    .consumed(false)
                    .build();

            String authUrl = UriComponentsBuilder.fromUriString("https://x.com/i/oauth2/authorize")
                    .queryParam("response_type", "code")
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_uri", redirectUri)
                    .queryParam("scope", "tweet.write tweet.read users.read offline.access")
                    .queryParam("state", state)
                    .queryParam("code_challenge", codeChallenge)
                    .queryParam("code_challenge_method", "S256")
                    .build()
                    .toUriString();

            return oAuth2AuthRequestRepository.save(oauth2AuthRequest)
                    .doOnSuccess(saved -> log.info("Saved OAuth2AuthRequest for userId {}", userId))
                    .thenReturn(authUrl);

        } catch (Exception e) {
            log.error("Error building authorization URL: ", e);
            return Mono.error(e);
        }
    }

    public Mono<XTokenResponse> exchangeCodeForToken(OAuth2AuthRequest request, String code) {

        String credentials = clientId + ":" + clientSecret;
        String encodedCredentials = java.util.Base64.getEncoder()
                .encodeToString(credentials.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        return xWebClient.post()
                .uri("/2/oauth2/token")
                .header("Authorization", "Basic " + encodedCredentials)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "authorization_code")
                        .with("code", code)
                        .with("client_id", clientId)
                        .with("redirect_uri", redirectUri)
                        .with("code_verifier", request.getCodeVerifier()))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new IllegalStateException("X token exchange failed: " + body)))
                )
                .bodyToMono(XTokenResponse.class)
                .doOnSuccess(token -> log.info("Successfully exchanged code for token"))
                .doOnError(e -> log.error("Token exchange failed: ", e));
    }

    public Mono<ResponseEntity<Object>> saveSocialAccountAndMarkConsumed(
            OAuth2AuthRequest request,
            XTokenResponse xTokenResponse) {

        UUID userId = request.getUserId();
        if (userId == null) {
            return Mono.just(ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body("Invalid request: userId is null"));
        }

        return getUserInfoFromX(xTokenResponse.getAccessToken())
                .flatMap(xUserInfo ->
                        Mono.fromCallable(() -> {
                                    Map<String, Object> tokenData = new HashMap<>();
                                    tokenData.put("access_token", xTokenResponse.getAccessToken());
                                    tokenData.put("refresh_token", xTokenResponse.getRefreshToken());
//                                    tokenData.put("expires_in", xTokenResponse.getExpiresIn());
                                    tokenData.put("scope", xTokenResponse.getScope());
                                    return encryptionUtil.encrypt(tokenData);
                                })
                                .flatMap(encryptedAuthData -> {
                                    SocialAccount socialAccount = SocialAccount.builder()
                                            .userId(userId)
                                            .platform("X")
                                            .platformUserId(xUserInfo.getId())
                                            .username(xUserInfo.getUsername())
                                            .authData(encryptedAuthData)
                                            .expiresAt(calculateExpiryTime(xTokenResponse.getExpiresIn()))
                                            .isActive(true)
                                            .build();
                                    return socialAccountRepository.save(socialAccount);
                                })
                )
                .flatMap(savedAccount -> {
                    log.info("Request  with id {} is being set consumed" , request.getId());
                    request.setConsumed(true);
                    return oAuth2AuthRequestRepository.save(request);
                })
                .thenReturn(ResponseEntity
                        .status(HttpStatus.FOUND)
                        .location(frontendRedirect("/auth/success"))
                        .build())
                .onErrorResume(e -> {
                    log.error("Error saving social account for userId {}: ", userId, e);
                    return Mono.just(ResponseEntity
                            .status(HttpStatus.FOUND)
                            .location(frontendRedirectWithMessage("/auth/error", e.getMessage()))
                            .build());
                });
    }

    public Mono<XUserInfo> getUserInfoFromX(String accessToken) {
        return xWebClient.get()
                .uri("/2/users/me?user.fields=id,username,name")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(body -> Mono.error(new IllegalStateException("X user info fetch failed: " + body)))
                )
                .bodyToMono(XUserInfo.class)
                .doOnSuccess(userInfo -> log.info("Fetched X user info: id={}, username={}",
                        userInfo.getId(), userInfo.getUsername()))
                .doOnError(e -> log.error("Failed to fetch X user info: ", e));
    }

    public Mono<Boolean> checkConnectionStatus(UUID userId) {
        return socialAccountRepository.findByUserIdAndPlatform(userId, "X")
                .map(SocialAccount::getIsActive)
                .defaultIfEmpty(false);
    }

    private OffsetDateTime calculateExpiryTime(Long expiresIn) {
        if (expiresIn == null) {
            return OffsetDateTime.now().plusDays(60);
        }
        return OffsetDateTime.now().plusSeconds(expiresIn);
    }

    private URI frontendRedirect(String path) {
        String base = normalizeFrontendBase(frontendUrl);
        String p = normalizePath(path);
        return URI.create(base + p);
    }

    private URI frontendRedirectWithMessage(String path, String message) {
        return UriComponentsBuilder
                .fromUri(frontendRedirect(path))
                .queryParam("message", message == null ? "Unknown error" : message)
                .build()
                .toUri();
    }

    private String normalizeFrontendBase(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("Missing app.frontend-url property");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) return "/auth/error";
        return path.startsWith("/") ? path : "/" + path;
    }
}
