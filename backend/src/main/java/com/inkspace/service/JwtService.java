package com.inkspace.service;

import com.inkspace.common.security.AuthUser;
import com.inkspace.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Access Token 的签发与校验（JWT，HS256，无状态）。
 * 只放 id/username/role，不放敏感信息——JWT 的 payload 是 Base64 编码，任何人可解。
 */
@Service
public class JwtService {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_ROLE = "role";

    private final SecretKey key;
    private final Duration accessTtl;

    public JwtService(JwtProperties properties) {
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(bytes);
        this.accessTtl = Duration.ofMinutes(properties.getAccessTtlMinutes());
    }

    public String createAccessToken(AuthUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLE, user.getRole())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 Access Token；签名错误、过期、格式非法都会抛 {@link JwtException}。
     */
    public AuthUser parseAccessToken(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new AuthUser(
                Long.valueOf(claims.getSubject()),
                claims.get(CLAIM_USERNAME, String.class),
                claims.get(CLAIM_ROLE, String.class));
    }

    public long getAccessTtlSeconds() {
        return accessTtl.toSeconds();
    }
}
