package com.moyamoya.domain.child.dto.response;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.entity.MoyaType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이 프로필 응답")
public record ChildResponse(
        @Schema(description = "아이 ID", example = "2") Long childId,
        @Schema(description = "아이 별명", example = "서아") String nickname,
        @Schema(description = "나이 구간", example = "AGE_5") AgeGroup ageGroup,
        @Schema(description = "모야 유형", example = "DEFAULT") MoyaType moyaType
) {
    public static ChildResponse from(Child child) {
        return new ChildResponse(child.getId(), child.getNickname(), child.getAgeGroup(), child.getMoyaType());
    }
}
