package com.moyamoya.domain.question.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AnalyzeQuestionRequest(
        @NotNull(message = "childId가 필요합니다.")
        Long childId,
        @NotBlank(message = "인식된 문장을 입력해 주세요.")
        String recognizedSentence
) {
}
