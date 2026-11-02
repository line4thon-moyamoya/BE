package com.moyamoya.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Access Token 재발급 요청")
public record TokenRefreshRequest(
        @Schema(description = "로그인 또는 이전 재발급에서 받은 Refresh Token")
        String refreshToken
) {
}
