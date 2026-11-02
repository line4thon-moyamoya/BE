package com.moyamoya.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SignupRequest(
        @Schema(description = "보호자 이메일", example = "jiwoo.mom@email.com")
        String email,

        @Schema(description = "보호자 비밀번호", example = "password123!")
        String password,

        @Schema(description = "보호자 모드 PIN", example = "1234")
        String parentPin,

        @Schema(description = "아이 프로필")
        ChildSignupRequest child,

        @Schema(description = "약관 동의 내역")
        List<ConsentAgreementRequest> consents
) {
}
