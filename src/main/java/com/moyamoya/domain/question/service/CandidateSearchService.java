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
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CandidateSearchService {

    private static final double EXTERNAL_SEARCH_THRESHOLD = 0.85;
    private static final double F1_MIN_PRONUNCIATION_SCORE = 0.60;
    private static final double F2_MIN_PRONUNCIATION_SCORE = 0.45;
    private static final double F2_MIN_FINAL_SCORE = 0.50;

    private static final double F1_MIN_RESULT_SCORE = 0.65;
    private static final double F1_STRONG_SCORE = 0.90;
    private static final double F1_STRONG_GAP = 0.12;
    private static final double F1_ADDITIONAL_GAP = 0.15;

    private static final int F1_MAX_DICTIONARY_CALLS = 10;
    private static final int F2_MAX_DICTIONARY_CALLS = 25;

    private static final double F2_STRONG_SCORE = 0.85;
    private static final double F2_STRONG_GAP = 0.15;
    private static final double F2_ADDITIONAL_GAP = 0.18;
    private static final int F2_MAX_CONTEXT_CANDIDATES = 15;

    private final CandidateRankingService rankingService;
    private final WordRepository wordRepository;
    private final ExternalDictionaryService externalDictionaryService;
    private final ExternalContextScoringService externalContextScoringService;
    private final PronunciationVariantGenerator pronunciationVariantGenerator;
    private final BadWordFilterService badWordFilterService;

    public List<WordCandidate> searchFirstCandidates(
            List<String> targetExpressions
    ) {
        List<WordCandidate> curatedCandidates =
                rankingService.rankFirst(targetExpressions);

        List<WordCandidate> storedExternalCandidates =
                searchStoredExternalCandidates(
                        targetExpressions,
                        null,
                        true
                );

        List<WordCandidate> existingCandidates =
                mergeUniqueCandidates(
                        curatedCandidates,
                        storedExternalCandidates
                ).stream()
                        .filter(this::isSafeCandidate)
                        .toList();

        // 강한 후보가 없는 경우에만 발음 변형 탐색
        boolean includeVariants =
                shouldTriggerExternalSearch(existingCandidates);

        // 정확 검색은 강한 후보 여부와 관계없이 수행
        List<WordCandidate> dictionaryCandidates =
                searchNewDictionaryCandidates(
                        targetExpressions,
                        null,
                        true,
                        includeVariants
                );

        List<WordCandidate> allCandidates =
                mergeUniqueCandidates(
                        existingCandidates,
                        dictionaryCandidates
                );

        return selectFinalCandidates(
                allCandidates,
                targetExpressions,
                true
        );
    }

    public List<WordCandidate> searchSecondCandidates(
            List<String> targetExpressions,
            String originalText,
            HeardFrom heardFrom,
            Set<String> excludedKeys
    ) {
        List<WordCandidate> curatedCandidates =
                rankingService.rankSecond(
                targetExpressions,
                originalText,
                heardFrom,
                excludedKeys
        );
        List<WordCandidate> storedExternalCandidates =
                searchStoredExternalCandidates(
                targetExpressions,
                excludedKeys,
                false
        );
        List<WordCandidate> dictionaryCandidates =
                searchNewDictionaryCandidates(
                        targetExpressions,
                        excludedKeys,
                        false,
                        true
                );

        List<WordCandidate> externalCandidates =
                mergeUniqueCandidates(
                        storedExternalCandidates,
                        dictionaryCandidates
                ).stream()
                        .filter(this::isSafeCandidate)
                        .toList();

        List<WordCandidate> scoredExternalCandidates =
                applyExternalContextScores(
                        originalText,
                        heardFrom,
                        externalCandidates
                );

        List<WordCandidate> allCandidates =
                mergeUniqueCandidates(
                        curatedCandidates,
                        scoredExternalCandidates
                );

        return selectFinalCandidates(
                allCandidates,
                targetExpressions,
                false
        );
    }

    private List<WordCandidate> applyExternalContextScores(
            String originalText,
            HeardFrom heardFrom,
            List<WordCandidate> externalCandidates
    ) {
        if (externalCandidates == null || externalCandidates.isEmpty()) {
            return List.of();
        }

        List<WordCandidate> evaluationTargets =
                externalCandidates.stream()
                        .filter(this::isSafeCandidate)
                        .filter(candidate ->
                                candidate.sourceType()
                                        == WordSourceType.EXTERNAL
                        )
                        .filter(candidate ->
                                candidate.pronunciationScore()
                                        >= F2_MIN_PRONUNCIATION_SCORE
                        )
                        .sorted(
                                Comparator.comparingDouble(
                                                WordCandidate::pronunciationScore
                                        ).reversed()
                                        .thenComparing(WordCandidate::word)
                        )
                        .limit(F2_MAX_CONTEXT_CANDIDATES)
                        .toList();

        if (evaluationTargets.isEmpty()) {
            return List.of();
        }

        Map<String, Double> contextScores =
                externalContextScoringService.scoreCandidates(
                        originalText,
                        heardFrom,
                        evaluationTargets
                );

        return evaluationTargets.stream()
                .map(candidate -> {
                    Double contextScore =
                            contextScores.get(candidate.candidateId());

                    double finalScore =
                            rankingService.calculateSecondPassScore(
                                    candidate.pronunciationScore(),
                                    contextScore,
                                    null
                            );

                    return candidate.withScores(
                            contextScore == null
                                    ? 0.0 : contextScore,
                            finalScore
                    );
                })
                .filter(candidate ->
                        candidate.finalScore() >= F2_MIN_FINAL_SCORE
                )
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

            if (badWordFilterService.containsBlockedText(normalizedWord)
                    || badWordFilterService.containsBlockedText(
                    word.getDefinition()
            )) {
                continue;
            }

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
                    normalizedWord,
                    isFirstPass
            );

            if (pronunciationScore < minPronunciationScore) {
                continue;
            }

            double contextScore = 0.0;
            double finalScore = pronunciationScore;

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
            Set<String> excludedKeys,
            boolean isFirstPass,
            boolean includeVariants
    ) {
        if (targetExpressions == null
                || targetExpressions.isEmpty()) {
            return List.of();
        }

        List<WordCandidate> candidates = new ArrayList<>();
        Set<String> existingWordNames = new LinkedHashSet<>();
        Set<String> seenQueries = new LinkedHashSet<>();

        // 1. 기존 DB 외부 단어
        wordRepository.findAllBySourceType(
                        WordSourceType.EXTERNAL
                ).stream()
                .map(Word::getWord)
                .filter(StringUtils::hasText)
                .map(this::normalizeKey)
                .forEach(existingWordNames::add);

        // 2. 기존 CSV 단어
        rankingService.getWords().stream()
                .map(QuestionWordData::word)
                .filter(StringUtils::hasText)
                .map(this::normalizeKey)
                .forEach(existingWordNames::add);

        // 3. 정확 검색 + 선택적 발음 변형 검색
        List<String> dictionaryQueries =
                buildDictionaryQueries(
                        targetExpressions,
                        isFirstPass,
                        includeVariants
                );

        int maxCalls = isFirstPass
                ? F1_MAX_DICTIONARY_CALLS
                : F2_MAX_DICTIONARY_CALLS;

        int dictionaryCalls = 0;

        double minPronunciationScore = isFirstPass
                ? F1_MIN_PRONUNCIATION_SCORE
                : F2_MIN_PRONUNCIATION_SCORE;

        for (String query : dictionaryQueries) {

            if (!StringUtils.hasText(query)) {
                continue;
            }

            String normalizedQuery = query.trim();
            String queryKey = normalizeKey(normalizedQuery);

            // 중복 검색 방지
            if (!seenQueries.add(queryKey)) {
                continue;
            }

            // 기존 단어 재검색 방지
            if (existingWordNames.contains(queryKey)) {
                continue;
            }

            // F1에서 이미 보여준 단어 제외
            if (isExcludedWord(normalizedQuery, excludedKeys)) {
                continue;
            }

            // 금칙 검색어 제외
            if (badWordFilterService.containsBlockedText(
                    normalizedQuery
            )) {
                continue;
            }

            // 실제 외부 사전 호출량 제한
            if (dictionaryCalls >= maxCalls) {
                break;
            }

            dictionaryCalls++;

            List<DictionaryEntry> entries =
                    externalDictionaryService.searchExactMatches(
                            normalizedQuery
                    );

            if (entries == null || entries.isEmpty()) {
                continue;
            }

            for (DictionaryEntry entry : entries) {

                if (entry == null
                        || !StringUtils.hasText(entry.word())) {
                    continue;
                }

                String candidateWord = entry.word().trim();
                String candidateKey = normalizeKey(candidateWord);

                // 실제 검색어와 표제어가 일치하는지 확인
                if (!normalizedQuery.equals(candidateWord)) {
                    continue;
                }

                if (existingWordNames.contains(candidateKey)) {
                    continue;
                }

                if (isExcludedWord(
                        candidateWord,
                        excludedKeys
                )) {
                    continue;
                }

                // 부적절한 단어와 뜻 제외
                if (badWordFilterService.containsBlockedText(
                        candidateWord
                ) || badWordFilterService.containsBlockedText(
                        entry.definition()
                )) {
                    continue;
                }

                double pronunciationScore =
                        rankingService
                                .calculateExternalPronunciationScore(
                                        targetExpressions,
                                        candidateWord,
                                        isFirstPass
                                );

                if (pronunciationScore
                        < minPronunciationScore) {
                    continue;
                }

                candidates.add(new WordCandidate(
                        "DICTIONARY:" + candidateWord,
                        null,
                        candidateWord,
                        WordSourceType.EXTERNAL,
                        pronunciationScore,
                        0.0,
                        pronunciationScore,
                        entry.definition(),
                        null,
                        null,
                        null,
                        null
                ));
            }
        }

        // 4. 표제어별 중복 제거 후 반환
        return candidates.stream()
                .collect(Collectors.toMap(
                        candidate -> normalizeKey(
                                candidate.word()
                        ),
                        candidate -> candidate,
                        (existing, replacement) ->
                                replacement.finalScore()
                                        > existing.finalScore()
                                        ? replacement
                                        : existing,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .sorted(Comparator
                        .comparingDouble(
                                WordCandidate::finalScore
                        ).reversed()
                        .thenComparing(
                                Comparator.comparingDouble(
                                        WordCandidate::pronunciationScore
                                ).reversed()
                        )
                        .thenComparing(WordCandidate::word))
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

    private boolean isExcludedWord(
            String word,
            Set<String> excludedKeys
    ) {
        if (excludedKeys == null || excludedKeys.isEmpty()) {
            return false;
        }

        String key = normalizeKey(word);

        return excludedKeys.stream()
                .filter(Objects::nonNull)
                .map(this::normalizeKey)
                .anyMatch(excluded ->
                        excluded.equals(key)
                                || excluded.endsWith(":" + key)
                );
    }

    private String normalizeKey(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<WordCandidate> selectFinalCandidates(
            List<WordCandidate> candidates,
            List<String> targetExpressions,
            boolean isFirstPass
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        double minScore = isFirstPass
                ? F1_MIN_RESULT_SCORE
                : F2_MIN_FINAL_SCORE;

        // 1. 최소 점수 이상인 후보만 선정
        List<WordCandidate> ranked = candidates.stream()
                .filter(this::isSafeCandidate)
                .filter(candidate ->
                        candidate.finalScore() >= minScore
                )
                .sorted(
                        Comparator.comparingDouble(
                                        WordCandidate::finalScore
                                ).reversed()
                                .thenComparing(
                                        Comparator.comparingDouble(
                                                WordCandidate::pronunciationScore
                                        ).reversed()
                                )
                                .thenComparing(WordCandidate::word)
                )
                .toList();

        if (ranked.isEmpty()) {
            return List.of();
        }

        // 2. F1: 조사 제거 후 표제어 정확 일치 우선
        if (isFirstPass && targetExpressions != null) {
            List<WordCandidate> exactMatches = ranked.stream()
                    .filter(candidate ->
                            targetExpressions.stream()
                                    .filter(Objects::nonNull)
                                    .map(String::trim)
                                    .anyMatch(expression ->
                                            expression.equals(candidate.word())
                                    )
                    )
                    .toList();

            if (!exactMatches.isEmpty()) {
                return exactMatches.stream()
                        .limit(3)
                        .toList();
            }
        }

        // 3. 후보가 하나뿐이라면 그대로 반환
        if (ranked.size() == 1) {
            return List.of(ranked.get(0));
        }

        WordCandidate best = ranked.get(0);
        WordCandidate second = ranked.get(1);

        double strongScore = isFirstPass
                ? F1_STRONG_SCORE
                : F2_STRONG_SCORE;

        double strongGap = isFirstPass
                ? F1_STRONG_GAP
                : F2_STRONG_GAP;

        // 4. 1위가 확실히 우세하면 하나만 반환
        if (best.finalScore() >= strongScore
                && best.finalScore() - second.finalScore() >= strongGap) {
            return List.of(best);
        }

        // 5. 1위와 점수 차이가 작은 후보만 추가
        double additionalGap = isFirstPass
                ? F1_ADDITIONAL_GAP
                : F2_ADDITIONAL_GAP;

        return ranked.stream()
                .filter(candidate ->
                        best.finalScore() - candidate.finalScore()
                                <= additionalGap
                )
                .limit(3)
                .toList();
    }

    private boolean isSafeCandidate(WordCandidate candidate) {
        return candidate != null
                && StringUtils.hasText(candidate.word())
                && !badWordFilterService.containsBlockedText(
                candidate.word()
        )
                && !badWordFilterService.containsBlockedText(
                candidate.definition()
        );
    }

    private List<String> buildDictionaryQueries(
            List<String> targetExpressions,
            boolean isFirstPass,
            boolean includeVariants
    ) {
        if (targetExpressions == null
                || targetExpressions.isEmpty()) {
            return List.of();
        }

        List<String> expressions = targetExpressions.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();

        LinkedHashSet<String> queries = new LinkedHashSet<>();

        // 1. 원본 및 조사 제거형 정확 검색 우선
        queries.addAll(expressions);

        if (!includeVariants) {
            return new ArrayList<>(queries);
        }

        // 2. 각 표현의 발음 변형 생성
        List<List<String>> variantGroups = expressions.stream()
                .map(expression ->
                        pronunciationVariantGenerator
                                .generateDictionaryQueries(
                                        expression,
                                        isFirstPass
                                )
                                .stream()
                                .filter(query ->
                                        !expressions.contains(query)
                                )
                                .toList()
                )
                .toList();

        int maxSize = variantGroups.stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        // 3. 표현별 변형 검색어를 번갈아 추가
        for (int i = 0; i < maxSize; i++) {
            for (List<String> group : variantGroups) {
                if (i < group.size()) {
                    queries.add(group.get(i));
                }
            }
        }

        return new ArrayList<>(queries);
    }
}
