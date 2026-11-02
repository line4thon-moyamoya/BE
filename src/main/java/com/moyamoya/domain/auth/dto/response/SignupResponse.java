package com.moyamoya.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record SignupResponse(
        @Schema(description = "생성된 보호자 ID", example = "1")
        Long userId,

        @Schema(description = "생성된 아이 ID", example = "1")
        Long childId,

        @Schema(description = "Access Token")
        String accessToken,

        @Schema(description = "Refresh Token")
        String refreshToken
) {
}
