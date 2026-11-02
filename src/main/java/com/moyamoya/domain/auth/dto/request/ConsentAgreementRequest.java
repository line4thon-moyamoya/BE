package com.moyamoya.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record ConsentAgreementRequest(
        @Schema(description = "동의 항목 ID", example = "1")
        Long consentId,

        @Schema(description = "동의 여부", example = "true")
        Boolean isAgreed
) {
}
