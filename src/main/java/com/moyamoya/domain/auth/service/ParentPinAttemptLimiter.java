package com.moyamoya.domain.auth.service;

import com.moyamoya.domain.auth.config.ParentPinProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ParentPinAttemptLimiter {

    private final ParentPinProperties properties;
    private final Clock clock;
    private final ConcurrentMap<Long, AttemptState> attempts = new ConcurrentHashMap<>();

    @Autowired
    public ParentPinAttemptLimiter(ParentPinProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ParentPinAttemptLimiter(ParentPinProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public AttemptReservation reserveAttempt(Long userId) {
        Instant now = clock.instant();
        AtomicReference<AttemptReservation> reservation = new AtomicReference<>();

        attempts.compute(userId, (key, current) -> {
            if (current != null
                    && current.blockedUntil() != null
                    && now.isBefore(current.blockedUntil())) {
                reservation.set(AttemptReservation.BLOCKED);
                return current;
            }

            if (current == null || current.blockedUntil() != null || isWindowExpired(current, now)) {
                return reserveFirstAttempt(now, reservation);
            }

            int failedAttempts = current.failedAttempts() + 1;
            Instant blockedUntil = failedAttempts >= properties.maxFailedAttempts()
                    ? now.plusMillis(properties.blockDurationMs())
                    : null;
            reservation.set(blockedUntil == null
                    ? AttemptReservation.ALLOWED
                    : AttemptReservation.FINAL_ALLOWED_ATTEMPT);
            return new AttemptState(failedAttempts, current.windowStartedAt(), blockedUntil);
        });

        return reservation.get();
    }

    public void reset(Long userId) {
        attempts.remove(userId);
    }

    private boolean isWindowExpired(AttemptState state, Instant now) {
        return !now.isBefore(state.windowStartedAt().plusMillis(properties.failureWindowMs()));
    }

    private AttemptState reserveFirstAttempt(
            Instant now,
            AtomicReference<AttemptReservation> reservation
    ) {
        Instant blockedUntil = properties.maxFailedAttempts() == 1
                ? now.plusMillis(properties.blockDurationMs())
                : null;
        reservation.set(blockedUntil == null
                ? AttemptReservation.ALLOWED
                : AttemptReservation.FINAL_ALLOWED_ATTEMPT);
        return new AttemptState(1, now, blockedUntil);
    }

    public enum AttemptReservation {
        ALLOWED,
        FINAL_ALLOWED_ATTEMPT,
        BLOCKED
    }

    private record AttemptState(
            int failedAttempts,
            Instant windowStartedAt,
            Instant blockedUntil
    ) {
    }
}
