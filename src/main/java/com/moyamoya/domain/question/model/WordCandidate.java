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
        String wordAudioContentType,
        String easyDescription
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
            String wordAudioBase64,
            String wordAudioContentType
    ) {
        this(
                candidateId, wordId, word, sourceType,
                pronunciationScore, contextScore, finalScore,
                definition, imageUrl, wordAudioUrl,
                wordAudioBase64, wordAudioContentType,
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
            String wordAudioUrl
    ) {
        this(
                candidateId, wordId, word, sourceType,
                pronunciationScore, contextScore, finalScore,
                definition, imageUrl, wordAudioUrl,
                null, null, null
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
                candidateId, wordId, word, sourceType,
                pronunciationScore, contextScore, finalScore,
                definition, imageUrl, wordAudioUrl,
                wordAudioBase64, null, null
        );
    }

    // 설명만 변경하고 기존 후보 정보는 유지
    public WordCandidate withEasyDescription(String description) {
        return new WordCandidate(
                candidateId, wordId, word, sourceType,
                pronunciationScore, contextScore, finalScore,
                definition, imageUrl, wordAudioUrl,
                wordAudioBase64, wordAudioContentType,
                description
        );
    }

    // 이미지, 오디오 정보 변경 시 설명도 유지
    public WordCandidate withMedia(
            Long newWordId,
            WordSourceType newSourceType,
            String newImageUrl,
            String newWordAudioUrl,
            String newWordAudioBase64,
            String newWordAudioContentType
    ) {
        return new WordCandidate(
                candidateId, newWordId, word, newSourceType,
                pronunciationScore, contextScore, finalScore,
                definition, newImageUrl, newWordAudioUrl,
                newWordAudioBase64, newWordAudioContentType,
                easyDescription
        );
    }
}