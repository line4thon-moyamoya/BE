package com.moyamoya.domain.question.dto.response;

import com.moyamoya.domain.question.model.WordCandidate;

import java.util.List;

public record SecondCandidateResponse(
        String sessionId,
        String status,
        int candidateCount,
        List<CandidateResponse> candidates,
        String nextStep
) {
    public static SecondCandidateResponse of(
            String sessionId,
            List<WordCandidate> candidates
    ) {
        List<CandidateResponse> responses = candidates.stream()
                .map(CandidateResponse::from)
                .toList();
        boolean found = !responses.isEmpty();

        return new SecondCandidateResponse(
                sessionId,
                found ? "CANDIDATES_FOUND" : "NO_CANDIDATE",
                responses.size(),
                responses,
                found ? "FINAL_CANDIDATE_SELECTION" : "WORD_REQUEST"
        );
    }
}
