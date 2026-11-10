package com.moyamoya.domain.user.dto.response;

import com.moyamoya.domain.child.dto.response.ChildResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "설정 화면 조회 응답")
public record UserSettingsResponse(
        @Schema(description = "전체 아이 프로필 목록. 등록된 아이가 없으면 빈 배열을 반환합니다.") List<ChildResponse> children,
        @Schema(description = "음성 수집 및 처리 동의 여부", example = "true") boolean voiceProcessingAgreed,
        @Schema(description = "서비스 개선을 위한 음성 활용 동의 여부", example = "false") boolean voiceImprovementAgreed
) {}
