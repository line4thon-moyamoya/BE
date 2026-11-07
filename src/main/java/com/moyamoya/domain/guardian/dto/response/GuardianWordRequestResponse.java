package com.moyamoya.domain.guardian.dto.response;

import com.moyamoya.domain.word.entity.WordRequest;

import java.time.LocalDateTime;

public record GuardianWordRequestResponse(
        Long wordRequestId,
        String recognizedText,
        String correctedWord,
        String audioUrl,
        String status,
        LocalDateTime createdAt,
        LocalDateTime resolvedAt
) {
    public static GuardianWordRequestResponse from(
            WordRequest request,
            String audioUrl
    ) {
        return new GuardianWordRequestResponse(
                request.getId(),
                request.getRecognizedText(),
                request.getCorrectedWord(),
                audioUrl,
                request.getStatus().name(),
                request.getCreatedAt(),
                request.getResolvedAt()
        );
    }
}
