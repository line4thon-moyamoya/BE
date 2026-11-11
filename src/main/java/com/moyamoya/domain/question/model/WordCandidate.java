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
        String wordAudioBase64,
        String wordAudioContentType
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
            String wordAudioUrl
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
                null,
                null
        );
    }

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
            String wordAudioBase64
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
                wordAudioBase64,
                null
        );
    }
}
