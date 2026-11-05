package com.moyamoya.domain.question.dto.response;

import com.moyamoya.domain.question.model.WordCandidate;

import java.util.List;

public record SecondCandidateResponse(
        String sessionId,
        String status,
        int candidateCount,
        List<WordCandidate> candidates,
        String nextStep
) {
    public static SecondCandidateResponse of(
            String sessionId,
            List<WordCandidate> candidates
    ) {
        boolean found = !candidates.isEmpty();

        return new SecondCandidateResponse(
                sessionId,
                found ? "CANDIDATES_FOUND" : "NO_CANDIDATE",
                candidates.size(),
                candidates,
                found ? "FINAL_CANDIDATE_SELECTION" : "WORD_REQUEST"
        );
    }
}
