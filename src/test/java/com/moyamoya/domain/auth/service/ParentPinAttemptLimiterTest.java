package com.moyamoya.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyamoya.domain.auth.config.ParentPinProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ParentPinAttemptLimiterTest {

    @Test
    void blocksUserAfterConfiguredNumberOfFailures() {
        ParentPinAttemptLimiter limiter = new ParentPinAttemptLimiter(
                new ParentPinProperties(5, 300_000L, 300_000L),
                Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(limiter.recordFailure(1L)).isFalse();
        assertThat(limiter.recordFailure(1L)).isFalse();
        assertThat(limiter.recordFailure(1L)).isFalse();
        assertThat(limiter.recordFailure(1L)).isFalse();
        assertThat(limiter.recordFailure(1L)).isTrue();
        assertThat(limiter.isBlocked(1L)).isTrue();

        limiter.reset(1L);
        assertThat(limiter.isBlocked(1L)).isFalse();
    }
}
