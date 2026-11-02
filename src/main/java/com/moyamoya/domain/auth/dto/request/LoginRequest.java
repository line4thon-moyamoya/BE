package com.moyamoya.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보호자 로그인 요청")
public record LoginRequest(
        @Schema(description = "보호자 이메일", example = "jiwoo.mom@email.com")
        String email,

        @Schema(description = "보호자 비밀번호", example = "password123!")
        String password
) {
}
