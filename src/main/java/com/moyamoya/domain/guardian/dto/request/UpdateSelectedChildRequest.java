package com.moyamoya.domain.guardian.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateSelectedChildRequest(
        @NotNull(message = "아이 ID를 입력해 주세요.")
        Long childId
) {
}
