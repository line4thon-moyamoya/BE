package com.moyamoya.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";
    private static final String PARENT_MODE_TOKEN_TYPE = "PARENT_MODE";

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey signingKey;

    @Autowired
    public JwtTokenProvider(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtTokenProvider(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedTokens issueTokens(Long userId) {
        return new IssuedTokens(
                createToken(userId, ACCESS_TOKEN_TYPE, properties.accessTokenExpirationMs()),
                createToken(userId, REFRESH_TOKEN_TYPE, properties.refreshTokenExpirationMs())
        );
    }

    public String issueParentModeToken(Long userId) {
        return createToken(
                userId,
                PARENT_MODE_TOKEN_TYPE,
                properties.parentModeTokenExpirationMs()
        );
    }

    public Long getUserIdFromAccessToken(String token) {
        return getUserIdFromToken(token, ACCESS_TOKEN_TYPE);
    }

    public Long getUserIdFromRefreshToken(String token) {
        return getUserIdFromToken(token, REFRESH_TOKEN_TYPE);
    }

    private String createToken(Long userId, String tokenType, long expirationMs) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusMillis(expirationMs);

        return Jwts.builder()
                .subject(userId.toString())
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Long getUserIdFromToken(String token, String expectedTokenType) {
        Claims claims = parseClaims(token);
        String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
        if (!expectedTokenType.equals(tokenType)) {
            throw new JwtException("올바른 토큰 타입이 아닙니다.");
        }

        return Long.valueOf(claims.getSubject());
    }
}
