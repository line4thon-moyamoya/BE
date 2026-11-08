package com.moyamoya.domain.question.dto.response;

import com.moyamoya.domain.word.entity.WordRequest;

public record WordRequestSaveResponse(
        Long wordRequestId,
        String status,
        String nextStep
) {
    public static WordRequestSaveResponse from(
            WordRequest request
    ) {
        return new WordRequestSaveResponse(
                request.getId(),
                request.getStatus().name(),
                "WORD_REQUEST_SAVED"
        );
    }
}