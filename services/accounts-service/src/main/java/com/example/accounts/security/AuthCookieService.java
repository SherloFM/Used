package com.example.accounts.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AuthCookieService {
    private final String name;
    private final boolean secure;
    private final Duration lifetime;

    public AuthCookieService(
            @Value("${security.auth-cookie.name}") String name,
            @Value("${security.auth-cookie.secure}") boolean secure,
            @Value("${security.jwt.expiration}") Duration lifetime) {
        this.name = name;
        this.secure = secure;
        this.lifetime = lifetime;
    }

    public String readToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (name.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public ResponseCookie issue(String token) {
        return cookie(token, lifetime);
    }

    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true).secure(secure).sameSite("Lax")
                .path("/").maxAge(maxAge).build();
    }
}
