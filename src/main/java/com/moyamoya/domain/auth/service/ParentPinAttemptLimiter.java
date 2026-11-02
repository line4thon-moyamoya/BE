package com.moyamoya.domain.auth.service;

import com.moyamoya.domain.auth.config.ParentPinProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    public boolean isBlocked(Long userId) {
        AttemptState state = attempts.get(userId);
        if (state == null) {
            return false;
        }

        Instant now = clock.instant();
        if (state.blockedUntil() != null && now.isBefore(state.blockedUntil())) {
            return true;
        }

        if (state.blockedUntil() != null || isWindowExpired(state, now)) {
            attempts.remove(userId, state);
        }
        return false;
    }

    public boolean recordFailure(Long userId) {
        Instant now = clock.instant();
        AttemptState state = attempts.compute(userId, (key, current) -> {
            if (current == null || current.blockedUntil() != null || isWindowExpired(current, now)) {
                return new AttemptState(1, now, null);
            }

            int failedAttempts = current.failedAttempts() + 1;
            Instant blockedUntil = failedAttempts >= properties.maxFailedAttempts()
                    ? now.plusMillis(properties.blockDurationMs())
                    : null;
            return new AttemptState(failedAttempts, current.windowStartedAt(), blockedUntil);
        });

        return state.blockedUntil() != null && now.isBefore(state.blockedUntil());
    }

    public void reset(Long userId) {
        attempts.remove(userId);
    }

    private boolean isWindowExpired(AttemptState state, Instant now) {
        return !now.isBefore(state.windowStartedAt().plusMillis(properties.failureWindowMs()));
    }

    private record AttemptState(
            int failedAttempts,
            Instant windowStartedAt,
            Instant blockedUntil
    ) {
    }
}
