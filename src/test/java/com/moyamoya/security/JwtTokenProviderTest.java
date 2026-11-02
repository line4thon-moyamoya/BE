package com.moyamoya.security;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes-long";

    @Test
    void issueTokensCreatesAccessAndRefreshTokensForUser() {
        Instant now = Instant.parse("2099-11-02T00:00:00Z");
        JwtProperties properties = new JwtProperties(SECRET, 60_000L, 120_000L);
        JwtTokenProvider provider = new JwtTokenProvider(
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        IssuedTokens tokens = provider.issueTokens(1L);

        Claims accessClaims = parse(tokens.accessToken());
        Claims refreshClaims = parse(tokens.refreshToken());
        assertThat(accessClaims.getSubject()).isEqualTo("1");
        assertThat(accessClaims.get("tokenType")).isEqualTo("ACCESS");
        assertThat(accessClaims.getExpiration()).isEqualTo(java.util.Date.from(now.plusSeconds(60)));
        assertThat(refreshClaims.getSubject()).isEqualTo("1");
        assertThat(refreshClaims.get("tokenType")).isEqualTo("REFRESH");
        assertThat(refreshClaims.getExpiration()).isEqualTo(java.util.Date.from(now.plusSeconds(120)));
    }

    @Test
    void springCreatesJwtTokenProviderBeanWithPropertiesConstructor() {
        JwtProperties properties = new JwtProperties(SECRET, 60_000L, 120_000L);

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JwtProperties.class, () -> properties);
            context.register(JwtTokenProvider.class);
            context.refresh();

            assertThat(context.getBean(JwtTokenProvider.class)).isNotNull();
        }
    }

    private Claims parse(String token) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
