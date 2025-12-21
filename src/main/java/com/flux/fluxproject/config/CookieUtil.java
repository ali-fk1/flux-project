//package com.flux.fluxproject.config;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Component;
//
//@Component
//public class CookieUtil {
//
//    @Value("${cookie.secure:false}")
//    private boolean secure;
//
//    @Value("${cookie.same-site:Strict}")
//    private String sameSite;
//
//    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
//    private static final int REFRESH_TOKEN_MAX_AGE = 30 * 24 * 60 * 60; // 30 days in seconds
//
//    /**
//     * Creates a Set-Cookie header string for the refresh token with security flags.
//     *
//     * @param token The refresh token value
//     * @return Set-Cookie header string
//     */
//    public String createRefreshTokenCookie(String token) {
//        StringBuilder cookie = new StringBuilder();
//        cookie.append(REFRESH_TOKEN_COOKIE_NAME).append("=").append(token).append("; ");
//        cookie.append("HttpOnly; ");
//        cookie.append("Path=/; ");
//        cookie.append("Max-Age=").append(REFRESH_TOKEN_MAX_AGE).append("; ");
//        cookie.append("SameSite=").append(sameSite).append("; ");
//
//        if (secure) {
//            cookie.append("Secure");
//        }
//
//        return cookie.toString();
//    }
//
//    /**
//     * Creates a Set-Cookie header string to clear the refresh token cookie.
//     *
//     * @return Set-Cookie header string that clears the cookie
//     */
//    public String clearRefreshTokenCookie() {
//        StringBuilder cookie = new StringBuilder();
//        cookie.append(REFRESH_TOKEN_COOKIE_NAME).append("=; ");
//        cookie.append("HttpOnly; ");
//        cookie.append("Path=/; ");
//        cookie.append("Max-Age=0; ");
//        cookie.append("SameSite=").append(sameSite).append("; ");
//
//        if (secure) {
//            cookie.append("Secure");
//        }
//
//        return cookie.toString();
//    }
//}

package com.flux.fluxproject.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CookieUtil {

    @Value("${cookie.secure}")
    private boolean secure;

    @Value("${cookie.same-site}")
    private String sameSite;

    private static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    private static final String ACCESS_TOKEN_COOKIE_NAME = "access_token";

    private static final int REFRESH_TOKEN_MAX_AGE = 30 * 24 * 60 * 60; // 30 days
    private static final int ACCESS_TOKEN_MAX_AGE = 15 * 60; // 15 minutes (MUST match JWT expiry)

    /* =======================
       REFRESH TOKEN COOKIES
       ======================= */

    public String createRefreshTokenCookie(String token) {
        return buildCookie(
                REFRESH_TOKEN_COOKIE_NAME,
                token,
                REFRESH_TOKEN_MAX_AGE
        );
    }

    public String clearRefreshTokenCookie() {
        return clearCookie(REFRESH_TOKEN_COOKIE_NAME);
    }

    /* =======================
       ACCESS TOKEN COOKIES
       ======================= */

    public String createAccessTokenCookie(String token) {
        return buildCookie(
                ACCESS_TOKEN_COOKIE_NAME,
                token,
                ACCESS_TOKEN_MAX_AGE
        );
    }

    public String clearAccessTokenCookie() {
        return clearCookie(ACCESS_TOKEN_COOKIE_NAME);
    }

    /* =======================
       INTERNAL HELPERS
       ======================= */

    private String buildCookie(String name, String value, int maxAge) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append("=").append(value).append("; ");
        cookie.append("HttpOnly; ");
        cookie.append("Path=/; ");
        cookie.append("Max-Age=").append(maxAge).append("; ");
        cookie.append("SameSite=").append(sameSite).append("; ");

        if (secure) {
            cookie.append("Secure");
        }

        return cookie.toString();
    }

    private String clearCookie(String name) {
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append("=; ");
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