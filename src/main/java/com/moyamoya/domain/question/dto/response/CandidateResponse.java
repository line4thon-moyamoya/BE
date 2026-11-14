// 공통 후보 추출 응답 DTO
package com.moyamoya.domain.question.dto.response;

import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.WordSourceType;

public record CandidateResponse(
        String candidateId,
        Long wordId,
        String word,
        String description,
        String imageUrl,
        String wordAudioUrl,
        String wordAudioBase64,
        String wordAudioContentType,
        boolean canCheckOtherMeanings
) {
    public static CandidateResponse from(
            WordCandidate candidate,
            int candidateCount
    ) {
        // 쉬운 설명이 없으면 사전 정의를 대신 사용
        String description = candidate.easyDescription() != null
                && !candidate.easyDescription().isBlank()
                ? candidate.easyDescription()
                : candidate.definition();

        boolean canCheckOtherMeanings =
                candidateCount == 1
                        && candidate.wordId() == null
                        && candidate.sourceType() == WordSourceType.EXTERNAL;

        return new CandidateResponse(
                candidate.candidateId(),
                candidate.wordId(),
                candidate.word(),
                description,
                candidate.imageUrl(),
                candidate.wordAudioUrl(),
                candidate.wordAudioBase64(),
                candidate.wordAudioContentType(),
                canCheckOtherMeanings
        );
    }
}