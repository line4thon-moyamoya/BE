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
        String description = candidate.easyDescription() != null
                && !candidate.easyDescription().isBlank()
                ? candidate.easyDescription()
                : null;

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
