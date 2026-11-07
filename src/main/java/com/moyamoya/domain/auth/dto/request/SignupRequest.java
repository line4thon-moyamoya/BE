package com.moyamoya.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "보호자 회원가입 요청")
public record SignupRequest(
        @Schema(description = "보호자 이메일", example = "jiwoo.mom@email.com")
        String email,

        @Schema(description = "보호자 비밀번호. 서버에서 암호화하여 저장합니다.", example = "password123!")
        String password,

        @Schema(description = "보호자 모드에서 사용하는 숫자 4자리 PIN", example = "1234")
        String parentPin,

        @Schema(description = "최초 등록할 아이 프로필")
        ChildSignupRequest child,

        @Schema(description = "약관 동의 내역. 필수 약관 ID 1~4는 모두 true여야 합니다.")
        List<ConsentAgreementRequest> consents
) {
}
