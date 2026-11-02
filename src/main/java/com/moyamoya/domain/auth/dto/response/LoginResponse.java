package com.moyamoya.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보호자 로그인 응답")
public record LoginResponse(
        @Schema(description = "보호자 ID", example = "1")
        Long userId,

        @Schema(description = "현재 선택된 아이 ID. 선택 이력이 없으면 가장 먼저 등록한 아이 ID입니다.", example = "1")
        Long selectedChildId,

        @Schema(description = "인증이 필요한 API 호출에 사용하는 Access Token")
        String accessToken,

        @Schema(description = "Access Token 재발급에 사용하는 Refresh Token")
        String refreshToken
) {
}
