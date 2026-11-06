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
        List<CandidateResponse> candidates,
        Dialogue dialogue,
        String nextStep
) {
    public record Dialogue(
            String text,
            String audioUrl,
            String audioBase64
    ) {
    }

    public static QuestionCandidateResponse of(
            QuestionSession session,
            String analysisType
    ) {
        return of(session, analysisType, null);
    }

    public static QuestionCandidateResponse of(
            QuestionSession session,
            String analysisType,
            String dialogueAudioBase64
    ) {
        List<WordCandidate> sourceCandidates = switch (session.getStatus()) {
            case SECOND_CANDIDATES_READY, SECOND_NO_CANDIDATES -> session.getSecondCandidatesForClient();
            default -> session.getFirstCandidatesForClient();
        };

        List<CandidateResponse> candidates = sourceCandidates.stream()
                .map(CandidateResponse::from)
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
                found ? new Dialogue(buildDialogue(candidates), null, dialogueAudioBase64) : null,
                found ? "CANDIDATE_SELECTION" : "ASK_HEARD_FROM"
        );
    }

    public static QuestionCandidateResponse forTest(
            QuestionSession session,
            String analysisType
    ) {
        return of(session, analysisType);
    }

    public static String buildDialogue(List<CandidateResponse> candidates) {
        if (candidates.size() == 1) {
            return candidates.getFirst().word() + " 말하는 거야?";
        }

        String words = String.join(
                "? ",
                candidates.stream().map(CandidateResponse::word).toList()
        );

        return words + "? 어떤 거야?";
    }
}
