package com.flux.fluxproject.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${cookie.secure:true}")
    private boolean secure;

    @Value("${cookie.same-site:Strict}")
    private String sameSite;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final int REFRESH_TOKEN_MAX_AGE = 30 * 24 * 60 * 60; // 30 days in seconds

    /**
     * Creates a Set-Cookie header string for the refresh token with security flags.
     *
     * @param token The refresh token value
     * @return Set-Cookie header string
     */
    public String createRefreshTokenCookie(String token) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(REFRESH_TOKEN_COOKIE_NAME).append("=").append(token).append("; ");
        cookie.append("HttpOnly; ");
        cookie.append("Path=/; ");
        cookie.append("Max-Age=").append(REFRESH_TOKEN_MAX_AGE).append("; ");
        cookie.append("SameSite=").append(sameSite).append("; ");

        if (secure) {
            cookie.append("Secure");
        }

        return cookie.toString();
    }

    /**
     * Creates a Set-Cookie header string to clear the refresh token cookie.
     *
     * @return Set-Cookie header string that clears the cookie
     */
    public String clearRefreshTokenCookie() {
        StringBuilder cookie = new StringBuilder();
        cookie.append(REFRESH_TOKEN_COOKIE_NAME).append("=; ");
        cookie.append("HttpOnly; ");
        cookie.append("Path=/; ");
        cookie.append("Max-Age=0; ");
        cookie.append("SameSite=").append(sameSite).append("; ");

        if (secure) {
            cookie.append("Secure");
        }

        return cookie.toString();
    }
}