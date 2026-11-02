package com.moyamoya.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약관별 동의 여부")
public record ConsentAgreementRequest(
        @Schema(
                description = "약관 ID (1 이용약관, 2 개인정보, 3 법정대리인, 4 음성 수집·이용, 5 음성 개선 활용)",
                example = "1"
        )
        Long consentId,

        @Schema(description = "동의 여부. ID 1~4는 필수 동의 항목입니다.", example = "true")
        Boolean isAgreed
) {
}
