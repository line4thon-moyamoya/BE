package com.moyamoya.domain.consent.dto.response;

import com.moyamoya.domain.consent.entity.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "동의 상태 변경 응답")
public record ConsentStatusResponse(
        @Schema(description = "동의 항목 유형", example = "VOICE_PROCESSING")
        ConsentType consentType,

        @Schema(description = "변경된 동의 여부", example = "false")
        boolean isAgreed
) {
}
