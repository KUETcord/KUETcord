package com.kuetcord.auth.service;

import com.kuetcord.auth.model.Role;
import com.kuetcord.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * Issues and verifies stateless HMAC-SHA256 JWT access tokens containing user identity and platform roles.
 */
@Service
public class JwtTokenService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenService(
            @Value("${kuetcord.jwt.secret}") String secret,
            @Value("${kuetcord.jwt.expiration-ms:86400000}") long expirationMs) {
        this.signingKey = resolveKey(secret);
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .toList();

        return Jwts.builder()
                .subject(user.getEmail())
                .claim("uid", user.getId())
                .claim("roles", roleNames)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidate(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return parseAndValidate(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object uid = parseAndValidate(token).get("uid");
        if (uid instanceof Number num) {
            return num.longValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        Object roles = parseAndValidate(token).get("roles");
        if (roles instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    private static SecretKey resolveKey(String secret) {
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) {
                return Keys.hmacShaKeyFor(decoded);
            }
        } catch (IllegalArgumentException ignored) {
            // Fall through to raw UTF-8 encoding if secret is not Base64
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}

