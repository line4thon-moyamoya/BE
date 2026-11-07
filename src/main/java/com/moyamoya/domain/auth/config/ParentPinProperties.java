package com.moyamoya.domain.auth.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "parent-pin")
public record ParentPinProperties(
        @Positive int maxFailedAttempts,
        @Positive long failureWindowMs,
        @Positive long blockDurationMs
) {
}
