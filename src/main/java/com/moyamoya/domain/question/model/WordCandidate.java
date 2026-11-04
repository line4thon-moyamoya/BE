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
        String wordAudioUrl
) {
}
