package com.moyamoya.domain.question.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmWordRequest(
        @NotBlank String candidateId
) {
}
