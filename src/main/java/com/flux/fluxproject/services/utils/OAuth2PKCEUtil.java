package com.flux.fluxproject.services.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public class OAuth2PKCEUtil {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final String PKCE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";

    /**
     * Generates a cryptographically secure code verifier for PKCE.
     * The verifier is 78 characters long using base64url encoding.
     *
     * @return A random code verifier string
     */
    public static String generateCodeVerifier(int length) {
        if (length < 43 || length > 128) {
            throw new IllegalArgumentException("PKCE code_verifier must be between 43 and 128 characters.");
        }

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(PKCE_CHARS.length());
            sb.append(PKCE_CHARS.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Generates a code challenge from the given code verifier using SHA-256.
     *
     * @param codeVerifier The code verifier to hash
     * @return The base64url-encoded SHA-256 hash of the verifier
     * @throws NoSuchAlgorithmException If SHA-256 is not available
     */
    public static String generateCodeChallenge(String codeVerifier)
            throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(codeVerifier.getBytes());
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(hash);
    }

    /**
     * Generates a cryptographically secure random state parameter.
     *
     * @return A random state string
     */
    public static String generateState() {
        byte[] bytes = new byte[32];
        SecureRandom random = new SecureRandom();
        random.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}