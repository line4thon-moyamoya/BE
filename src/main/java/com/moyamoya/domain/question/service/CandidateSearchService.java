package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.model.DictionaryEntry;
import com.moyamoya.domain.question.model.QuestionWordData;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.HeardFrom;
import com.moyamoya.domain.word.entity.Word;
import com.moyamoya.domain.word.entity.WordSourceType;
import com.moyamoya.domain.word.repository.WordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidateSearchService {

    private static final double EXTERNAL_SEARCH_THRESHOLD = 0.85;
    private static final double F1_MIN_PRONUNCIATION_SCORE = 0.60;
    private static final double F2_MIN_PRONUNCIATION_SCORE = 0.45;
    private static final double F2_MIN_FINAL_SCORE = 0.50;

    private final CandidateRankingService rankingService;
    private final WordRepository wordRepository;
    private final ExternalDictionaryService externalDictionaryService;
    private final PronunciationVariantGenerator pronunciationVariantGenerator;
    private final PronunciationSimilarity pronunciationSimilarity;
    private final ContextScoringService contextScoringService;

    public List<WordCandidate> searchFirstCandidates(List<String> targetExpressions) {
        List<WordCandidate> curatedCandidates = rankingService.rankFirst(targetExpressions);
        List<WordCandidate> storedExternalCandidates = searchStoredExternalCandidates(
                targetExpressions,
                null,
                null,
                null,
                true
        );
        List<WordCandidate> existingCandidates = mergeUniqueCandidates(
                curatedCandidates,
                storedExternalCandidates
        );

        if (!shouldTriggerExternalSearch(existingCandidates)) {
            return existingCandidates.stream()
                    .limit(3)
                    .toList();
        }

        List<WordCandidate> dictionaryCandidates = searchNewDictionaryCandidates(
                targetExpressions,
                null,
                null,
                null,
                true
        );

        return mergeUniqueCandidates(
                existingCandidates,
                dictionaryCandidates
        ).stream()
                .limit(3)
                .toList();
    }

    public List<WordCandidate> searchSecondCandidates(
            List<String> targetExpressions,
            String originalText,
            HeardFrom heardFrom,
            Set<String> excludedKeys
    ) {
        List<WordCandidate> curatedCandidates = rankingService.rankSecond(
                targetExpressions,
                originalText,
                heardFrom,
                excludedKeys
        );
        List<WordCandidate> storedExternalCandidates = searchStoredExternalCandidates(
                targetExpressions,
                originalText,
                heardFrom,
                excludedKeys,
                false
        );
        List<WordCandidate> existingCandidates = mergeUniqueCandidates(
                curatedCandidates,
                storedExternalCandidates
        );

        List<WordCandidate> dictionaryCandidates = searchNewDictionaryCandidates(
                targetExpressions,
                originalText,
                heardFrom,
                excludedKeys,
                false
        );

        return mergeUniqueCandidates(
                existingCandidates,
                dictionaryCandidates
        ).stream()
                .limit(3)
                .toList();
    }

    public boolean shouldTriggerExternalSearch(List<WordCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }

        WordCandidate best = candidates.stream()
                .filter(Objects::nonNull)
                .max(Comparator
                        .comparingDouble(WordCandidate::pronunciationScore)
                        .thenComparingDouble(WordCandidate::finalScore))
                .orElse(null);

        if (best == null) {
            return true;
        }

        boolean hasExactCandidate = candidates.stream()
                .filter(Objects::nonNull)
                .anyMatch(candidate -> candidate.pronunciationScore() >= 0.99
                        && candidate.word() != null
                        && !candidate.word().isBlank());

        return !hasExactCandidate && best.pronunciationScore() < EXTERNAL_SEARCH_THRESHOLD;
    }

    private List<WordCandidate> searchStoredExternalCandidates(
            List<String> targetExpressions,
            String originalText,
            HeardFrom heardFrom,
            Set<String> excludedKeys,
            boolean isFirstPass
    ) {
        if (targetExpressions == null || targetExpressions.isEmpty()) {
            return List.of();
        }

        List<WordCandidate> candidates = new ArrayList<>();
        Set<String> seenWords = new LinkedHashSet<>();
        Set<String> csvWords = rankingService.getWords().stream()
                .map(QuestionWordData::word)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
        double minPronunciationScore = isFirstPass ? F1_MIN_PRONUNCIATION_SCORE : F2_MIN_PRONUNCIATION_SCORE;

        for (Word word : wordRepository.findAllBySourceType(WordSourceType.EXTERNAL)) {
            if (word == null || word.getWord() == null || word.getWord().isBlank()) {
                continue;
            }

            String normalizedWord = word.getWord().trim();
            if (seenWords.contains(normalizedWord)) {
                continue;
            }
            seenWords.add(normalizedWord);

            if (csvWords.contains(normalizedWord.toLowerCase())) {
                continue;
            }

            if (isExcludedWord(normalizedWord, excludedKeys)) {
                continue;
            }

            double pronunciationScore = rankingService.calculateExternalPronunciationScore(
                    targetExpressions,
                    normalizedWord
            );

            if (pronunciationScore < minPronunciationScore) {
                continue;
            }

            double contextScore = 0.0;
            if (originalText != null && !originalText.isBlank()) {
                contextScore = contextScoringService.calculateContextScore(
                        originalText,
                        new com.moyamoya.domain.question.model.QuestionWordData(
                                normalizedWord,
                                normalizedWord,
                                null,
                                heardFrom == null ? null : heardFrom.name(),
                                null,
                                null,
                                null,
                                normalizedWord,
                                normalizedWord,
                                null,
                                word.getDefinition(),
                                null,
                                word.getDescAge3To4(),
                                word.getDescAge5(),
                                null
                        )
                );
            }

            double sourceScore = 0.0;
            if (heardFrom != null && heardFrom != HeardFrom.UNKNOWN) {
                sourceScore = contextScoringService.calculateSourceScore(heardFrom,
                        new com.moyamoya.domain.question.model.QuestionWordData(
                                normalizedWord,
                                normalizedWord,
                                null,
                                heardFrom.name(),
                                null,
                                null,
                                null,
                                normalizedWord,
                                normalizedWord,
                                null,
                                word.getDefinition(),
                                null,
                                word.getDescAge3To4(),
                                word.getDescAge5(),
                                null
                        ));
            }

            double finalScore = isFirstPass
                    ? pronunciationScore
                    : calculateSecondPassScore(pronunciationScore, contextScore, sourceScore);

            if (!isFirstPass && finalScore < F2_MIN_FINAL_SCORE) {
                continue;
            }

            candidates.add(new WordCandidate(
                    "EXTERNAL:" + normalizedWord,
                    word.getId(),
                    normalizedWord,
                    WordSourceType.EXTERNAL,
                    pronunciationScore,
                    contextScore,
                    finalScore,
                    word.getDefinition(),
                    word.getImageUrl(),
                    word.getWordAudioUrl(),
                    null,
                    null
            ));
        }

        return candidates;
    }

    private List<WordCandidate> searchNewDictionaryCandidates(
            List<String> targetExpressions,
            String originalText,
            HeardFrom heardFrom,
            Set<String> excludedKeys,
            boolean isFirstPass
    ) {
        if (targetExpressions == null || targetExpressions.isEmpty()) {
            return List.of();
        }

        Set<String> seenWords = new LinkedHashSet<>();
        Set<String> seenQueries = new LinkedHashSet<>();
        List<WordCandidate> candidates = new ArrayList<>();
        Set<String> existingWordNames = new LinkedHashSet<>();

        wordRepository.findAllBySourceType(WordSourceType.EXTERNAL).stream()
                .map(Word::getWord)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .forEach(existingWordNames::add);

        rankingService.getWords().stream()
                .map(QuestionWordData::word)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .forEach(existingWordNames::add);

        for (String expression : targetExpressions) {
            if (expression == null || expression.isBlank()) {
                continue;
            }

            String normalized = expression.trim();
            if (!seenWords.add(normalized)) {
                continue;
            }

            List<String> dictionaryQueries = pronunciationVariantGenerator.generateDictionaryQueries(normalized);

            for (String query : dictionaryQueries) {
                if (query == null || query.isBlank() || !seenQueries.add(query)) {
                    continue;
                }

                // 이미 Word 마스터 또는 CSV에 있는 단어는 외부 사전에 다시 요청하지 않음
                if (existingWordNames.contains(query.trim().toLowerCase())) {
                    continue;
                }

                List<DictionaryEntry> entries = externalDictionaryService.searchExactMatches(query);
                if (entries == null || entries.isEmpty()) {
                    continue;
                }

                for (DictionaryEntry entry : entries) {
                    if (entry == null || !StringUtils.hasText(entry.word())) {
                        continue;
                    }

                    String candidateWord = entry.word().trim();
                    if (candidateWord.isBlank()) {
                        continue;
                    }

                    if (existingWordNames.contains(candidateWord.toLowerCase())) {
                        continue;
                    }

                    if (isExcludedWord(candidateWord, excludedKeys)) {
                        continue;
                    }

                    double pronunciationScore = rankingService.calculateExternalPronunciationScore(
                            targetExpressions,
                            candidateWord
                    );
                    double minPronunciationScore = isFirstPass ? F1_MIN_PRONUNCIATION_SCORE : F2_MIN_PRONUNCIATION_SCORE;
                    if (pronunciationScore < minPronunciationScore) {
                        continue;
                    }

                    double contextScore = 0.0;
                    if (originalText != null && !originalText.isBlank()) {
                        contextScore = contextScoringService.calculateContextScore(
                                originalText,
                                new com.moyamoya.domain.question.model.QuestionWordData(
                                        candidateWord,
                                        candidateWord,
                                        null,
                                        heardFrom == null ? null : heardFrom.name(),
                                        null,
                                        null,
                                        null,
                                        candidateWord,
                                        candidateWord,
                                        null,
                                        entry.definition(),
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        );
                    }

                    double finalScore = isFirstPass
                            ? pronunciationScore
                            : calculateSecondPassScore(pronunciationScore, contextScore, 0.0);

                    if (!isFirstPass && finalScore < F2_MIN_FINAL_SCORE) {
                        continue;
                    }

                    candidates.add(new WordCandidate(
                            "DICTIONARY:" + candidateWord,
                            null,
                            candidateWord,
                            WordSourceType.EXTERNAL,
                            pronunciationScore,
                            contextScore,
                            finalScore,
                            entry.definition(),
                            null,
                            null,
                            null,
                            null
                    ));
                }
            }
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble(WordCandidate::finalScore).reversed()
                        .thenComparing(Comparator.comparingDouble(WordCandidate::pronunciationScore).reversed())
                        .thenComparing(WordCandidate::word))
                .limit(3)
                .toList();
    }

    private List<WordCandidate> mergeUniqueCandidates(
            List<WordCandidate> curatedCandidates,
            List<WordCandidate> externalCandidates
    ) {
        Map<String, WordCandidate> merged = new LinkedHashMap<>();

        curatedCandidates.forEach(candidate ->
                merged.putIfAbsent(
                        normalizeKey(candidate.word()),
                        candidate
                )
        );

        externalCandidates.forEach(candidate ->
                merged.putIfAbsent(
                        normalizeKey(candidate.word()),
                        candidate
                )
        );

        // 정확히 일치하는 후보만 남기지 않고 모든 후보를 점수순으로 정렬
        return merged.values().stream()
                .sorted(Comparator
                        .comparingDouble(WordCandidate::finalScore)
                        .reversed()
                        .thenComparing(
                                Comparator.comparingDouble(
                                        WordCandidate::pronunciationScore
                                ).reversed()
                        )
                        .thenComparing(WordCandidate::word))
                .toList();
    }

    private double calculateSecondPassScore(double pronunciationScore, double contextScore, double sourceScore) {
        double pronWeight = 0.60;
        double contextWeight = 0.25;
        double sourceWeight = 0.15;
        double totalWeight = pronWeight + contextWeight + sourceWeight;
        return Math.clamp(
                (pronunciationScore * pronWeight + contextScore * contextWeight + sourceScore * sourceWeight) / totalWeight,
                0.0,
                1.0
        );
    }

    private boolean isExcludedWord(String normalizedWord, Set<String> excludedKeys) {
        if (excludedKeys == null || excludedKeys.isEmpty()) {
            return false;
        }

        String key = normalizeKey(normalizedWord);
        return excludedKeys.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeKey)
                .anyMatch(excluded -> excluded.equalsIgnoreCase(key)
                        || excluded.endsWith(":" + key)
                        || excluded.equalsIgnoreCase("EXTERNAL:" + key)
                        || excluded.equalsIgnoreCase("CURATED:" + key));
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.trim();
    }
}
