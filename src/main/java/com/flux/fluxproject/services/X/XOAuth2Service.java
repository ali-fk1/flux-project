package com.flux.fluxproject.services.X;

import com.flux.fluxproject.domain.OAuth2AuthRequest;
import com.flux.fluxproject.repositories.OAuth2AuthRequestRepository;
import com.flux.fluxproject.services.utils.OAuth2PKCEUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class XOAuth2Service {

    private final WebClient.Builder webClientBuilder;

    @Value("${x.client-id}")
    private  String clientId;

    @Value("${x.redirect-uri}")
    private  String redirectUri;

    @Value("${x.code-verifier-length}")
    private int length;

    private final OAuth2AuthRequestRepository  oAuth2AuthRequestRepository;


    public Mono<String> buildAuthorizationUrl() {
        try{
            String codeVerifier = OAuth2PKCEUtil.generateCodeVerifier(length);
            String codeChallenge = OAuth2PKCEUtil.generateCodeChallenge(codeVerifier);
            String state = OAuth2PKCEUtil.generateState();

            OAuth2AuthRequest oauth2AuthRequest = OAuth2AuthRequest.builder()
                    .codeVerifier(codeVerifier)
                    .state(state)
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(600))
                    .build();

            String authUrl = UriComponentsBuilder.fromUriString("https://x.com/i/oauth2/authorize")
                    .queryParam("response_type","code")
                    .queryParam("client_id", clientId)
                    .queryParam("redirect_uri" , redirectUri)
                    .queryParam(
                            "scope",
                            "tweet.write tweet.read users.read offline.access"
                    )
                    .queryParam("state", state)
                    .queryParam("code_challenge" , codeChallenge)
                    .queryParam("code_challenge_method" , "S256")
                    .build()
                    .toUriString();
             return Mono.just(authUrl);
        }catch (Exception e){
            log.error("Error building Auth Url",e.getMessage());
            return Mono.error(e);
        }

    }




}
