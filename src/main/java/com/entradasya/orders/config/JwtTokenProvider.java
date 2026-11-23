package com.entradasya.orders.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;

    public JwtTokenProvider(@Value("${security.jwt.secret:super-secure-shared-secret-entradasya-2026}") String secret) {
        this.secretKey = generateKey(secret);
    }

    private SecretKey generateKey(String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            try {
                MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
                secretBytes = sha256.digest(secretBytes);
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("SHA-256 algorithm not available", e);
            }
        }
        return Keys.hmacShaKeyFor(secretBytes);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UserPrincipal extractUserPrincipal(String token) {
        Claims claims = getClaims(token);
        String subject = claims.getSubject();
        UUID userId = UUID.fromString(subject);
        String name = claims.get("name", String.class);
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);
        if (role == null) {
            role = claims.get("rol", String.class);
        }
        if (role == null) {
            role = "cliente";
        }
        return new UserPrincipal(userId, name, email, role, token);
    }

    public String generateToken(UUID userId, String name, String email, String role, long expirationMillis) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("name", name)
                .claim("email", email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(secretKey)
                .compact();
    }
}
