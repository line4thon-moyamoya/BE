package com.moyamoya.domain.guardian.dto.response;

import com.moyamoya.domain.word.entity.WordRequest;

import java.time.LocalDateTime;

public record ResolveWordResponse(
        Long wordRequestId,
        String recognizedText,
        String correctedWord,
        String status,
        LocalDateTime resolvedAt
) {
    public static ResolveWordResponse from(WordRequest request) {
        return new ResolveWordResponse(
                request.getId(),
                request.getRecognizedText(),
                request.getCorrectedWord(),
                request.getStatus().name(),
                request.getResolvedAt()
        );
    }
}
