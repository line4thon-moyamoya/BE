package com.moyamoya.domain.consent.dto.response;

import com.moyamoya.domain.consent.entity.Consent;
import com.moyamoya.domain.consent.entity.ConsentType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "동의 항목 응답")
public record ConsentResponse(
        @Schema(description = "동의 항목 ID", example = "1")
        Long consentId,

        @Schema(description = "동의 항목 유형", example = "TERMS_OF_SERVICE")
        ConsentType consentType,

        @Schema(description = "동의 항목 본문", example = "서비스 이용약관 내용")
        String content
) {

    public static ConsentResponse from(Consent consent) {
        return new ConsentResponse(
                consent.getId(),
                consent.getConsentType(),
                consent.getContent()
        );
    }
}
