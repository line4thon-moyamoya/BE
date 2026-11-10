package com.moyamoya.domain.child.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초기 아이 프로필 설정 요청")
public record InitialChildProfileRequest(
        @Schema(description = "모야 유형. DEFAULT, CAR, PRINCESS", example = "CAR", requiredMode = Schema.RequiredMode.REQUIRED)
        String moyaType
) {}
