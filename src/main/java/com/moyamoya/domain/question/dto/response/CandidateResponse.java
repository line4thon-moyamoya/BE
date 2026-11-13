// 공통 후보 추출 응답 DTO
package com.moyamoya.domain.question.dto.response;

import com.moyamoya.domain.question.model.WordCandidate;

public record CandidateResponse(
        String candidateId,
        Long wordId,
        String word,
        String description,
        String imageUrl,
        String wordAudioUrl,
        String wordAudioBase64,
        String wordAudioContentType
) {
    public static CandidateResponse from(WordCandidate candidate) {
        // 쉬운 설명이 없으면 사전 정의를 대신 사용
        String description = candidate.easyDescription() != null
                && !candidate.easyDescription().isBlank()
                ? candidate.easyDescription()
                : candidate.definition();

        return new CandidateResponse(
                candidate.candidateId(),
                candidate.wordId(),
                candidate.word(),
                description,
                candidate.imageUrl(),
                candidate.wordAudioUrl(),
                candidate.wordAudioBase64(),
                candidate.wordAudioContentType()
        );
    }
}
