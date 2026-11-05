package com.moyamoya.domain.question.dto.request;

public record TestSttRequest(
        Long childId,
        String recognizedSentence
) {
}
