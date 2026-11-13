package com.moyamoya.domain.question.dto.response;

public record ConfirmWordResponse(
        String status,
        Long wordId,
        String word,
        String description,
        String imageUrl,
        String wordAudioUrl,
        String nextStep
) {
}
