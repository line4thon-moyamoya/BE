package com.moyamoya.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보호자 PIN 인증 응답")
public record ParentPinVerifyResponse(
        @Schema(description = "보호자 모드 API 호출에 사용하는 단기 토큰")
        String parentModeToken
) {
}
