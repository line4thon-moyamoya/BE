package com.moyamoya.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access Token 재발급 응답")
public record TokenRefreshResponse(
        @Schema(description = "새로 발급된 Access Token")
        String accessToken,

        @Schema(description = "새로 발급된 Refresh Token. 기존 Refresh Token은 즉시 무효화됩니다.")
        String refreshToken
) {
}
