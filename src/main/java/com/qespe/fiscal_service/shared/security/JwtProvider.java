package com.qespe.fiscal_service.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class JwtProvider {

    private final JwtProperties props;

    @Value("${security.jwt.audience:pos-api}")
    private String expectedAudience;

    @Value("${security.jwt.enforce-audience:false}")
    private boolean enforceAudience;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    private Claims parseClaims(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (enforceAudience) {
            java.util.Set<String> aud = claims.getAudience();
            if (aud == null || !aud.contains(expectedAudience)) {
                throw new JwtException("Invalid token audience");
            }
        }
        return claims;
    }

    public String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    public UUID extractCompanyId(String token) {
        Claims claims = parseClaims(token);
        String value = (String) claims.get("companyId");
        return value != null ? UUID.fromString(value) : null;
    }

    public boolean extractSuperadmin(String token) {
        Boolean value = parseClaims(token).get("superadmin", Boolean.class);
        return value != null && value;
    }
}
