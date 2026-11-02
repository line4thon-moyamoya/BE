package com.moyamoya.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record ChildSignupRequest(
        @Schema(description = "아이 별명", example = "지우")
        String nickname,

        @Schema(description = "아이 나이 구간", example = "AGE_3_4")
        String ageGroup
) {
}
