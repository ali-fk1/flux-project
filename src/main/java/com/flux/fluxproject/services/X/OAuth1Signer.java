package com.flux.fluxproject.services.X;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
public class OAuth1Signer {
    private final String consumerKey;
    private final String consumerSecret;
    private final SecureRandom random = new SecureRandom();

    public OAuth1Signer(String consumerKey, String consumerSecret) {
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;

        log.info("OAuth1Signer initialized");
        if (consumerKey == null || consumerKey.trim().isEmpty()) {
            log.error("Consumer key is null or empty!");
        }
        if (consumerSecret == null || consumerSecret.trim().isEmpty()) {
            log.error("Consumer secret is null or empty!");
        }
    }

    public Map<String, String> buildOAuthParams(String callbackUrl, String oauthToken) {
        Map<String, String> params = new HashMap<>();
        params.put("oauth_consumer_key", consumerKey);
        params.put("oauth_nonce", generateNonce());
        params.put("oauth_signature_method", "HMAC-SHA1");
        params.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        params.put("oauth_version", "1.0");

        if (callbackUrl != null) {
            params.put("oauth_callback", callbackUrl);
        }

        if (oauthToken != null) {
            params.put("oauth_token", oauthToken);
        }

        log.debug("Built OAuth params: {}", params);
        return params;
    }

    public String sign(String method, String url, Map<String, String> params, String tokenSecret) {
        try {
            // Build parameter string
            String paramString = params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(Collectors.joining("&"));

            log.debug("Parameter string: {}", paramString);

            // Build signature base string
            String signatureBaseString = method.toUpperCase() + "&" +
                    encode(url) + "&" +
                    encode(paramString);

            log.debug("Signature base string: {}", signatureBaseString);

            // Build signing key
            String signingKey = encode(consumerSecret) + "&" +
                    (tokenSecret != null ? encode(tokenSecret) : "");

            log.debug("Signing key: {}...", signingKey.substring(0, Math.min(10, signingKey.length())));

            // Generate signature
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] signature = mac.doFinal(signatureBaseString.getBytes(StandardCharsets.UTF_8));
            String result = Base64.getEncoder().encodeToString(signature);

            log.debug("Generated signature: {}", result);
            return result;

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to generate OAuth signature", e);
            throw new RuntimeException("Failed to generate OAuth signature", e);
        }
    }

    public String buildAuthHeader(Map<String, String> params) {
        String headerValue = params.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("oauth_"))
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> encode(entry.getKey()) + "=\"" + encode(entry.getValue()) + "\"")
                .collect(Collectors.joining(", "));

        String result = "OAuth " + headerValue;
        log.debug("Built auth header: {}", result);
        return result;
    }

    private String generateNonce() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes).replaceAll("[^a-zA-Z0-9]", "");
        log.debug("Generated nonce: {}", nonce);
        return nonce;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }
}