package com.moyamoya.domain.question.model;

import com.moyamoya.domain.word.entity.WordSourceType;

public record WordCandidate(
        String candidateId,
        Long wordId,
        String word,
        WordSourceType sourceType,
        double pronunciationScore,
        double contextScore,
        double finalScore,
        String definition,
        String imageUrl,
        String wordAudioUrl,
        String descriptionAudioUrl,
        String wordAudioBase64,
        String descriptionAudioBase64
) {
    public WordCandidate(
            String candidateId,
            Long wordId,
            String word,
            WordSourceType sourceType,
            double pronunciationScore,
            double contextScore,
            double finalScore,
            String definition,
            String imageUrl,
            String wordAudioUrl,
            String descriptionAudioUrl
    ) {
        this(
                candidateId,
                wordId,
                word,
                sourceType,
                pronunciationScore,
                contextScore,
                finalScore,
                definition,
                imageUrl,
                wordAudioUrl,
                descriptionAudioUrl,
                null,
                null
        );
    }
}
