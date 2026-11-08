package com.moyamoya.domain.question.dto.response;

import com.moyamoya.domain.question.model.WordCandidate;

import java.util.List;

public record SecondCandidateResponse(
        String sessionId,
        String status,
        int candidateCount,
        List<CandidateResponse> candidates,
        Dialogue dialogue,
        String nextStep
) {
    public record Dialogue(
            String text,
            String audioUrl,
            String audioBase64,
            String audioContentType
    ) {
    }

    public static SecondCandidateResponse of(
            String sessionId,
            List<WordCandidate> candidates
    ) {
        return of(sessionId, candidates, null);
    }

    public static SecondCandidateResponse of(
            String sessionId,
            List<WordCandidate> candidates,
            String dialogueAudioBase64
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
                found ? new Dialogue(
                        QuestionCandidateResponse.buildDialogue(responses),
                        null,
                        dialogueAudioBase64,
                        hasText(dialogueAudioBase64) ? "audio/wav" : null
                ) : null,
                found ? "FINAL_CANDIDATE_SELECTION" : "WORD_REQUEST"
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
