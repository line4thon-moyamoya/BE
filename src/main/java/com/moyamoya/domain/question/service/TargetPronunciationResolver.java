package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.model.QuestionWordData;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.WordSourceType;
import com.moyamoya.domain.word.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TargetPronunciationResolver {

    private final CandidateRankingService rankingService;
    private final WordRepository wordRepository;

    private static final double MIN_PRONUNCIATION_SCORE = 0.85;
    private static final double MIN_SCORE_GAP = 0.12;

    // 기존 호출부 호환
    public Optional<String> resolve(
            List<String> expressions,
            List<WordCandidate> candidates
    ) {
        return resolve(expressions, candidates, true);
    }

    public Optional<String> resolve(
            List<String> expressions,
            List<WordCandidate> candidates,
            boolean isFirstPass
    ) {
        if (expressions == null || expressions.isEmpty()) {
            return Optional.empty();
        }

        List<String> normalizedExpressions =
                expressions.stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(expression ->
                                !expression.isBlank()
                        )
                        .distinct()
                        .toList();

        if (normalizedExpressions.isEmpty()) {
            return Optional.empty();
        }

        String original = normalizedExpressions.getFirst();

        if (normalizedExpressions.size() == 1) {
            return Optional.of(original);
        }

        List<WordCandidate> validCandidates =
                candidates == null
                        ? List.of()
                        : candidates.stream()
                          .filter(Objects::nonNull)
                          .toList();

        // 1. 원본이 실제 단어이면 원본 유지
        if (isKnownWord(original, validCandidates)) {
            return Optional.of(original);
        }

        // 2. 조사 제거형 중 정확 일치 단어 확인
        List<String> verified =
                normalizedExpressions.stream()
                        .skip(1)
                        .filter(expression ->
                                isKnownWord(
                                        expression,
                                        validCandidates
                                )
                        )
                        .toList();

        if (verified.size() == 1) {
            return Optional.of(verified.getFirst());
        }

        if (verified.size() > 1) {
            return Optional.of(original);
        }

        // 3. 비교할 후보가 없다면 원본 유지
        if (validCandidates.isEmpty()) {
            return Optional.of(original);
        }

        String bestExpression = original;

        double originalScore = calculateBestScore(
                original,
                validCandidates,
                isFirstPass
        );

        double bestScore = originalScore;
        double runnerUpScore = originalScore;

        // 4. 원본 및 조사 제거형의 발음 점수 비교
        for (String expression : normalizedExpressions.stream()
                .skip(1)
                .toList()) {

            double score = calculateBestScore(
                    expression,
                    validCandidates,
                    isFirstPass
            );

            if (score > bestScore) {

                runnerUpScore = bestScore;
                bestScore = score;
                bestExpression = expression;

            } else {

                runnerUpScore = Math.max(
                        runnerUpScore,
                        score
                );
            }
        }

        // 5. 충분히 확실할 때만 조사 제거
        if (!bestExpression.equals(original)
                && bestScore >= MIN_PRONUNCIATION_SCORE
                && bestScore - runnerUpScore >= MIN_SCORE_GAP) {

            return Optional.of(bestExpression);
        }

        return Optional.of(original);
    }

    private double calculateBestScore(
            String expression,
            List<WordCandidate> candidates,
            boolean isFirstPass
    ) {
        return candidates.stream()
                .mapToDouble(candidate -> {
                    if (candidate.sourceType() == WordSourceType.CURATED) {
                        Optional<QuestionWordData> csvWord =
                                findCsvWordData(candidate.word());

                        if (csvWord.isPresent()) {
                            return rankingService
                                    .calculateExternalPronunciationScore(
                                            List.of(expression),
                                            csvWord.get(),
                                            isFirstPass
                                    );
                        }
                    }

                    return rankingService
                            .calculateExternalPronunciationScore(
                                    List.of(expression),
                                    candidate.word(),
                                    isFirstPass
                            );
                })
                .max()
                .orElse(0.0);
    }

    private Optional<QuestionWordData> findCsvWordData(String word) {
        if (word == null || word.isBlank()) {
            return Optional.empty();
        }

        String normalized = word.trim();

        return rankingService.getWords().stream()
                .filter(Objects::nonNull)
                .filter(data -> data.word() != null)
                .filter(data -> normalized.equals(data.word().trim()))
                .findFirst();
    }

    private boolean isKnownWord(
            String expression,
            List<WordCandidate> candidates
    ) {
        // CSV 및 기존 DB 단어 확인
        if (rankingService.containsCsvWord(expression)
                || wordRepository.existsByWord(expression)) {

            return true;
        }

        // 이번 후보 탐색에서 검증된 신규 사전 단어 확인
        return candidates.stream()
                .map(WordCandidate::word)
                .filter(Objects::nonNull)
                .anyMatch(expression::equals);
    }
}