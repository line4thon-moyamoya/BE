package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.dto.response.QuestionCandidateResponse;
import com.moyamoya.domain.question.dto.response.SecondCandidateResponse;
import com.moyamoya.domain.question.exception.QuestionErrorCode;
import com.moyamoya.domain.question.extractor.JosaCandidateGenerator;
import com.moyamoya.domain.question.extractor.QuestionTargetExtractor;
import com.moyamoya.domain.question.model.QuestionExtractionResult;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.question.session.QuestionSession;
import com.moyamoya.domain.question.session.QuestionSessionStore;
import com.moyamoya.domain.question.session.QuestionSessionStatus;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.child.exception.ChildErrorCode;
import com.moyamoya.domain.child.repository.ChildRepository;
import com.moyamoya.domain.user.entity.User;
import com.moyamoya.domain.user.repository.UserRepository;
import com.moyamoya.domain.word.entity.HeardFrom;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.security.exception.SecurityErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionTargetExtractor targetExtractor;
    private final JosaCandidateGenerator josaGenerator;
    private final CandidateRankingService rankingService;
    private final QuestionSessionStore sessionStore;
    private final WordRepository wordRepository;
    private final ChildRepository childRepository;
    private final UserRepository userRepository;

    public QuestionCandidateResponse analyze(
            Long userId,
            Long childId,
            String recognizedSentence
    ) {
        if (userId == null) {
            throw new CustomException(QuestionErrorCode.INVALID_QUESTION_REQUEST);
        }
        verifyChildOwnership(userId, childId);
        return runFirstCandidateFlow(childId, recognizedSentence);
    }

    public QuestionCandidateResponse testStt(
            Long childId,
            String recognizedSentence
    ) {
        return runFirstCandidateFlow(childId, recognizedSentence);
    }

    public SecondCandidateResponse findSecondCandidates(
            Long userId,
            String sessionId,
            HeardFrom context
    ) {
        if (userId == null) {
            throw new CustomException(QuestionErrorCode.INVALID_QUESTION_REQUEST);
        }

        if (context == null) {
            throw new CustomException(QuestionErrorCode.INVALID_CONTEXT);
        }

        UUID parsedSessionId = parseSessionId(sessionId);
        QuestionSession session = sessionStore.find(parsedSessionId)
                .orElseThrow(() -> new CustomException(QuestionErrorCode.SESSION_NOT_FOUND));

        verifyChildOwnership(userId, session.getChildId());

        if (session.getStatus() != QuestionSessionStatus.FIRST_CANDIDATES_READY
                && session.getStatus() != QuestionSessionStatus.FIRST_NO_CANDIDATES) {
            throw new CustomException(QuestionErrorCode.INVALID_SESSION_STATE);
        }

        Set<String> excludedKeys = session.getFirstCandidates()
                .stream()
                .map(WordCandidate::candidateId)
                .collect(Collectors.toSet());

        List<WordCandidate> candidates = rankingService.rankSecond(
                session.getTargetExpressions(),
                session.getOriginalSentence(),
                context,
                excludedKeys
        );

        List<WordCandidate> enrichedCandidates = enrichCandidatesWithWordData(candidates);

        session.setSecondCandidates(context, enrichedCandidates);
        sessionStore.save(session);

        return SecondCandidateResponse.of(
                session.getSessionId().toString(),
                enrichedCandidates
        );
    }

    private QuestionCandidateResponse runFirstCandidateFlow(
            Long childId,
            String recognizedSentence
    ) {
        if (childId == null) {
            throw new CustomException(QuestionErrorCode.INVALID_QUESTION_REQUEST);
        }

        if (recognizedSentence == null || recognizedSentence.isBlank()) {
            throw new CustomException(QuestionErrorCode.INVALID_QUESTION_REQUEST);
        }

        String sentence = recognizedSentence.trim();

        QuestionSession session = QuestionSession.create(
                childId,
                sentence,
                null
        );

        QuestionExtractionResult extraction = targetExtractor.extract(sentence);

        if (!extraction.success()) {
            throw new CustomException(QuestionErrorCode.INVALID_QUESTION_REQUEST);
        }

        String recognizedText = extraction.recognizedText();

        List<String> expressions = josaGenerator.generate(recognizedText);

        session.setExtractionResult(recognizedText, expressions);

        rankingService.resolveTargetPronunciation(expressions)
                .ifPresent(session::updateTargetPronunciation);

        List<WordCandidate> candidates = rankingService.rankFirst(expressions);
        List<WordCandidate> enrichedCandidates = enrichCandidatesWithWordData(candidates);
        session.setFirstCandidates(enrichedCandidates);

        sessionStore.save(session);

        String analysisType = determineAnalysisType(session, enrichedCandidates);

        return QuestionCandidateResponse.forTest(session, analysisType);
    }

    private UUID parseSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new CustomException(QuestionErrorCode.INVALID_SESSION_ID);
        }
        try {
            return UUID.fromString(sessionId.trim());
        } catch (IllegalArgumentException e) {
            throw new CustomException(QuestionErrorCode.INVALID_SESSION_ID);
        }
    }

    private void verifyChildOwnership(Long userId, Long childId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(
                        SecurityErrorCode.AUTHENTICATION_REQUIRED
                ));

        if (childId == null) {
            throw new CustomException(QuestionErrorCode.INVALID_QUESTION_REQUEST);
        }

        Child child = childRepository.findById(childId)
                .orElseThrow(() -> new CustomException(ChildErrorCode.CHILD_NOT_FOUND));

        if (!child.getUser().getId().equals(user.getId())) {
            throw new CustomException(QuestionErrorCode.ACCESS_FORBIDDEN);
        }
    }

    private List<WordCandidate> enrichCandidatesWithWordData(
            List<WordCandidate> candidates
    ) {
        return candidates.stream()
                .map(candidate -> {
                    Word word = wordRepository.findByWord(candidate.word())
                            .orElse(null);

                    if (word == null) {
                        return candidate;
                    }

                    return new WordCandidate(
                            candidate.candidateId(),
                            word.getId(),
                            candidate.word(),
                            candidate.sourceType(),
                            candidate.pronunciationScore(),
                            candidate.contextScore(),
                            candidate.finalScore(),
                            word.getDefinition() != null ? word.getDefinition() : candidate.definition(),
                            word.getWordAudioUrl() != null ? word.getWordAudioUrl() : candidate.wordAudioUrl()
                    );
                })
                .toList();
    }

    private String determineAnalysisType(
            QuestionSession session,
            List<WordCandidate> candidates
    ) {
        if (candidates.isEmpty()) {
            return "NONE";
        }

        if (!session.getRecognizedText().equals(session.getTargetPronunciation())) {
            return "PARTICLE_REMOVAL";
        }

        return "PHONETIC";
    }
}
