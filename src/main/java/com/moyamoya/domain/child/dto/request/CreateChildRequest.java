package com.moyamoya.domain.child.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이 프로필 추가 요청")
public record CreateChildRequest(
        @Schema(description = "아이 별명. 앞뒤 공백 제외 1~50자", example = "서아")
        String nickname,
        @Schema(description = "나이 구간. AGE_3_4 또는 AGE_5", example = "AGE_5")
        String ageGroup,
        @Schema(description = "모야 유형. DEFAULT, CAR, PRINCESS. 생략 시 DEFAULT", example = "CAR")
        String moyaType
) {
}
