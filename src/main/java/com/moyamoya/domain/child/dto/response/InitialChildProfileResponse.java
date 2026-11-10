package com.moyamoya.domain.child.dto.response;

import com.moyamoya.domain.child.entity.MoyaType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "초기 아이 프로필 설정 응답")
public record InitialChildProfileResponse(
        @Schema(description = "아이 ID", example = "1") Long childId,
        @Schema(description = "선택한 모야 유형", example = "CAR") MoyaType moyaType
) {}
