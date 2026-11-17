package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TargetPronunciationResolver {

    private final CandidateRankingService rankingService;
    private final WordRepository wordRepository;
    private final ExternalDictionaryService dictionaryService;

    private static final double MIN_PRONUNCIATION_SCORE = 0.85;
    private static final double MIN_SCORE_GAP = 0.12;

    public Optional<String> resolve(
            List<String> expressions,
            List<WordCandidate> candidates
    ) {
        if (expressions == null || expressions.isEmpty()) {
            return Optional.empty();
        }

        String original = expressions.getFirst();

        if (expressions.size() == 1) {
            return Optional.of(original);
        }

        // 원본이 정상적인 단어이면 조사 제거하지 않음
        if (isKnownWord(original)) {
            return Optional.of(original);
        }

        List<String> verified = expressions.stream()
                .skip(1)
                .filter(this::isKnownWord)
                .distinct()
                .toList();

        // 정확 일치 조사 제거형이 하나인 경우
        if (verified.size() == 1) {
            return Optional.of(verified.getFirst());
        }

        // 여러 형태가 실제 단어이면 원본 유지
        if (verified.size() > 1) {
            return Optional.of(original);
        }

        // 정확 일치가 없으면 실제 탐색 후보와 비교
        if (candidates == null || candidates.isEmpty()) {
            return Optional.of(original);
        }

        String bestExpression = original;
        double originalScore = calculateBestScore(
                original, candidates
        );

        double bestScore = originalScore;
        double runnerUpScore = originalScore;

        for (String expression : expressions.stream()
                .skip(1).toList()) {

            double score = calculateBestScore(
                    expression, candidates
            );

            if (score > bestScore) {
                runnerUpScore = bestScore;
                bestScore = score;
                bestExpression = expression;
            } else {
                runnerUpScore = Math.max(
                        runnerUpScore, score
                );
            }
        }

        if (!bestExpression.equals(original)
                && bestScore >= MIN_PRONUNCIATION_SCORE
                && bestScore - runnerUpScore >= MIN_SCORE_GAP) {
            return Optional.of(bestExpression);
        }

        return Optional.of(original);
    }

    private double calculateBestScore(
            String expression,
            List<WordCandidate> candidates
    ) {
        return candidates.stream()
                .mapToDouble(candidate ->
                        rankingService
                                .calculateExternalPronunciationScore(
                                        List.of(expression),
                                        candidate.word()
                                )
                )
                .max()
                .orElse(0.0);
    }

    private boolean isKnownWord(String expression) {

        if (rankingService.containsCsvWord(expression)
                || wordRepository.existsByWord(expression)) {
            return true;
        }

        return dictionaryService.searchExactMatches(expression)
                .stream()
                .anyMatch(entry ->
                        expression.equals(entry.word())
                );
    }
}