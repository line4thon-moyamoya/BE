package com.moyamoya.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보호자 PIN 인증 요청")
public record ParentPinVerifyRequest(
        @Schema(description = "회원가입 시 설정한 숫자 4자리 보호자 PIN", example = "1234")
        String parentPin
) {
}
