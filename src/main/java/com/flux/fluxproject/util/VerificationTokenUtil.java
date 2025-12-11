package com.flux.fluxproject.util;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class VerificationTokenUtil {
    private static final SecureRandom SR = new SecureRandom();

    private VerificationTokenUtil() {}

    public static String generateRawToken() {
        byte[] b = new byte[32]; // 256-bit random
        SR.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte by : digest) sb.append(String.format("%02x", by));
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
