package com.moyamoya.domain.question.dto.response;

import java.util.List;
import java.util.UUID;

public record OtherMeaningsResponse(
        UUID sessionId,
        String candidateId,
        String word,
        boolean hasOtherMeanings,
        int meaningCount,
        List<Meaning> meanings
) {
    public record Meaning(
            String meaningId,
            String description
    ) {}

    public static OtherMeaningsResponse of(
            UUID sessionId,
            String candidateId,
            String word,
            List<Meaning> meanings
    ) {
        return new OtherMeaningsResponse(
                sessionId,
                candidateId,
                word,
                !meanings.isEmpty(),
                meanings.size(),
                meanings
        );
    }
}
