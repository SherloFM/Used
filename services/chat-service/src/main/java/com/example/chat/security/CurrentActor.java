package com.example.chat.security;

import java.security.Principal;
import java.util.List;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record CurrentActor(Long id, String username, String role) implements Principal {
    public CurrentActor {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("Invalid user ID");
        }
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new IllegalArgumentException("Invalid role");
        }
    }

    @Override
    public String getName() {
        return id.toString();
    }

    public static CurrentActor from(Claims claims) {
        Object value = claims.get("roles");
        if (!(value instanceof List<?> roles) || roles.size() != 1) {
            throw new IllegalArgumentException("Invalid roles claim");
        }
        return new CurrentActor(Long.valueOf(claims.getSubject()),
                claims.get("username", String.class), String.valueOf(roles.get(0)));
    }

    public List<SimpleGrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
