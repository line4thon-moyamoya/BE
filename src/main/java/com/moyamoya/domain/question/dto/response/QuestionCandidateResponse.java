package com.moyamoya.domain.question.dto.response;

import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.question.session.QuestionSession;
import com.moyamoya.domain.question.session.QuestionSessionStatus;

import java.util.List;
import java.util.UUID;

public record QuestionCandidateResponse(
        UUID sessionId,
        String status,
        String analysisType,
        String recognizedText,
        String extractedWord,
        int candidateCount,
        List<Candidate> candidates,
        Dialogue dialogue,
        String nextStep
) {
    public record Candidate(
            String candidateId,
            Long wordId,
            String word,
            String description,
            String imageUrl,
            String wordAudioUrl,
            String descriptionAudioUrl
    ) {
    }

    public record Dialogue(
            String text,
            String audioUrl
    ) {
    }

    public static QuestionCandidateResponse forTest(
            QuestionSession session,
            String analysisType
    ) {
        List<WordCandidate> sourceCandidates = switch (session.getStatus()) {
            case SECOND_CANDIDATES_READY, SECOND_NO_CANDIDATES -> session.getSecondCandidates();
            default -> session.getFirstCandidates();
        };

        List<Candidate> candidates = sourceCandidates.stream()
                .map(QuestionCandidateResponse::toCandidate)
                .toList();

        boolean found = session.getStatus() == QuestionSessionStatus.FIRST_CANDIDATES_READY
                || session.getStatus() == QuestionSessionStatus.SECOND_CANDIDATES_READY;

        String responseStatus = found ? "CANDIDATES_FOUND" : "NO_CANDIDATE";

        return new QuestionCandidateResponse(
                session.getSessionId(),
                responseStatus,
                found ? analysisType : "NONE",
                session.getOriginalSentence(),
                session.getTargetPronunciation(),
                candidates.size(),
                candidates,
                found ? new Dialogue(buildDialogue(candidates), null) : null,
                found ? "CANDIDATE_SELECTION" : "ASK_HEARD_FROM"
        );
    }

    private static Candidate toCandidate(WordCandidate candidate) {
        return new Candidate(
                candidate.candidateId(),
                candidate.wordId(),
                candidate.word(),
                candidate.definition(),
                null,
                candidate.wordAudioUrl(),
                null
        );
    }

    private static String buildDialogue(List<Candidate> candidates) {
        if (candidates.size() == 1) {
            return candidates.get(0).word() + " 말하는 거야?";
        }

        String words = String.join(
                "? ",
                candidates.stream().map(Candidate::word).toList()
        );

        return words + "? 어떤 거야?";
    }
}
