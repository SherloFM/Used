package com.example.accounts.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Service
public class JWTUtilities {
    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final Duration lifetime;

    public JWTUtilities(
            @Value("${security.jwt.secret}") String base64Secret,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.jwt.audience}") String audience,
            @Value("${security.jwt.expiration}") Duration lifetime) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        this.issuer = issuer;
        this.audience = audience;
        if (lifetime.isNegative() || lifetime.isZero()) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
        this.lifetime = lifetime;
    }

    public String generateJWTtoken(MyUserDetails details) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(details.getUser().getId().toString())
                .setIssuer(issuer)
                .setAudience(audience)
                .claim("roles", List.of(details.getUser().getRole().name()))
                .claim("username", details.getUser().getUsername())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plus(lifetime)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseAndValidate(String token) {
        Jws<Claims> parsed = Jwts.parserBuilder()
                .setSigningKey(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .setAllowedClockSkewSeconds(30)
                .build().parseClaimsJws(token);
        if (!"HS256".equals(parsed.getHeader().getAlgorithm())) {
            throw new UnsupportedJwtException("Unsupported signing algorithm");
        }
        Claims claims = parsed.getBody();
        if (claims.getExpiration() == null || claims.getIssuedAt() == null
                || !claims.getExpiration().after(claims.getIssuedAt())
                || claims.getIssuedAt().getTime() > System.currentTimeMillis() + 30_000L) {
            throw new MalformedJwtException("Invalid token timestamps");
        }
        try {
            long id = Long.parseLong(claims.getSubject());
            if (id <= 0 || !Long.toString(id).equals(claims.getSubject())) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            throw new MalformedJwtException("Invalid token subject");
        }
        Object roles = claims.get("roles");
        if (!(roles instanceof List<?> values) || values.isEmpty()
                || values.stream().anyMatch(role -> !"USER".equals(role) && !"ADMIN".equals(role))) {
            throw new MalformedJwtException("Invalid roles claim");
        }
        return claims;
    }
}
