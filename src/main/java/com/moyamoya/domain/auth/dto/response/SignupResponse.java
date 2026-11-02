package com.moyamoya.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보호자 회원가입 응답")
public record SignupResponse(
        @Schema(description = "생성된 보호자 ID", example = "1")
        Long userId,

        @Schema(description = "생성된 아이 ID", example = "1")
        Long childId,

        @Schema(description = "인증이 필요한 API 호출에 사용하는 Access Token")
        String accessToken,

        @Schema(description = "Access Token 재발급에 사용하는 Refresh Token")
        String refreshToken
) {
}
