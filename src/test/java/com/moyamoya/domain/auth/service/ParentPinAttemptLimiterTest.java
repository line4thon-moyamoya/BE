package com.moyamoya.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyamoya.domain.auth.config.ParentPinProperties;
import com.moyamoya.domain.auth.service.ParentPinAttemptLimiter.AttemptReservation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class ParentPinAttemptLimiterTest {

    @Test
    void blocksUserAfterConfiguredNumberOfFailures() {
        ParentPinAttemptLimiter limiter = new ParentPinAttemptLimiter(
                new ParentPinProperties(5, 300_000L, 300_000L),
                Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(limiter.reserveAttempt(1L)).isEqualTo(AttemptReservation.ALLOWED);
        assertThat(limiter.reserveAttempt(1L)).isEqualTo(AttemptReservation.ALLOWED);
        assertThat(limiter.reserveAttempt(1L)).isEqualTo(AttemptReservation.ALLOWED);
        assertThat(limiter.reserveAttempt(1L)).isEqualTo(AttemptReservation.ALLOWED);
        assertThat(limiter.reserveAttempt(1L))
                .isEqualTo(AttemptReservation.FINAL_ALLOWED_ATTEMPT);
        assertThat(limiter.reserveAttempt(1L)).isEqualTo(AttemptReservation.BLOCKED);

        limiter.reset(1L);
        assertThat(limiter.reserveAttempt(1L)).isEqualTo(AttemptReservation.ALLOWED);
    }

    @Test
    void reservesOnlyConfiguredNumberOfConcurrentAttempts() throws Exception {
        ParentPinAttemptLimiter limiter = new ParentPinAttemptLimiter(
                new ParentPinProperties(5, 300_000L, 300_000L),
                Clock.fixed(Instant.parse("2026-11-02T00:00:00Z"), ZoneOffset.UTC)
        );
        List<Callable<AttemptReservation>> tasks = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            tasks.add(() -> limiter.reserveAttempt(1L));
        }

        try (var executor = Executors.newFixedThreadPool(20)) {
            List<AttemptReservation> reservations = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    })
                    .toList();

            assertThat(reservations).filteredOn(AttemptReservation.ALLOWED::equals).hasSize(4);
            assertThat(reservations)
                    .filteredOn(AttemptReservation.FINAL_ALLOWED_ATTEMPT::equals)
                    .hasSize(1);
            assertThat(reservations).filteredOn(AttemptReservation.BLOCKED::equals).hasSize(15);
        }
    }
}
