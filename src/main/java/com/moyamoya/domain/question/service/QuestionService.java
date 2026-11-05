package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.dto.response.QuestionCandidateResponse;
import com.moyamoya.domain.question.extractor.JosaCandidateGenerator;
import com.moyamoya.domain.question.extractor.QuestionTargetExtractor;
import com.moyamoya.domain.question.model.QuestionExtractionResult;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.question.session.QuestionSession;
import com.moyamoya.domain.question.session.QuestionSessionStore;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.repository.WordRepository;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionService {

    private final QuestionTargetExtractor targetExtractor;
    private final JosaCandidateGenerator josaGenerator;
    private final CandidateRankingService rankingService;
    private final QuestionSessionStore sessionStore;
    private final WordRepository wordRepository;

    public QuestionCandidateResponse testStt(
            Long childId,
            String recognizedSentence
    ) {
        if (childId == null) {
            throw new CustomException(
                    GlobalErrorCode.INVALID_INPUT,
                    "childId가 필요합니다."
            );
        }

        if (recognizedSentence == null || recognizedSentence.isBlank()) {
            throw new CustomException(
                    GlobalErrorCode.INVALID_INPUT,
                    "인식된 문장이 없습니다."
            );
        }

        String sentence = recognizedSentence.trim();

        QuestionSession session = QuestionSession.create(
                childId,
                sentence,
                null
        );

        QuestionExtractionResult extraction = targetExtractor.extract(sentence);

        if (!extraction.success()) {
            throw new CustomException(
                    GlobalErrorCode.INVALID_INPUT,
                    "질문 추출 실패: " + extraction.failureReason()
            );
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
