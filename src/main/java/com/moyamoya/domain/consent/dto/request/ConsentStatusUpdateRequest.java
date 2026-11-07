package com.moyamoya.domain.consent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "동의 상태 변경 요청")
public record ConsentStatusUpdateRequest(
        @Schema(description = "동의 여부", example = "false")
        Boolean isAgreed
) {
}
