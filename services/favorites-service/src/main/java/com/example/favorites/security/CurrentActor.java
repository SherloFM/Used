package com.example.favorites.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

public record CurrentActor(Long id, String username, String role) {
    public CurrentActor {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid subject");
        }
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new IllegalArgumentException("Invalid role");
        }
    }

    public static CurrentActor from(Claims claims) {
        Object value = claims.get("roles");
        if (!(value instanceof List<?> roles) || roles.size() != 1) {
            throw new IllegalArgumentException("Invalid roles claim");
        }
        return new CurrentActor(Long.valueOf(claims.getSubject()),
                claims.get("username", String.class), String.valueOf(roles.get(0)));
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public List<SimpleGrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
