package com.moyamoya.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 시 등록할 아이 프로필")
public record ChildSignupRequest(
        @Schema(description = "아이 별명", example = "지우")
        String nickname,

        @Schema(description = "아이 나이 구간 (AGE_3_4 또는 AGE_5)", example = "AGE_3_4")
        String ageGroup
) {
}
