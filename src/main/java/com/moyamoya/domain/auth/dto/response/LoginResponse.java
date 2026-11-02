package com.moyamoya.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(
        @Schema(description = "보호자 ID", example = "1")
        Long userId,

        @Schema(description = "현재 선택된 아이 ID", example = "1")
        Long selectedChildId,

        @Schema(description = "Access Token")
        String accessToken,

        @Schema(description = "Refresh Token")
        String refreshToken
) {
}
