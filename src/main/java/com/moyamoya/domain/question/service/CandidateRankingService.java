// 119개 단어 대상으로 1차 후보 1~3개 선정 - 후보 점수 계산 및 정렬
package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.model.QuestionWordData;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.WordSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.moyamoya.domain.word.entity.HeardFrom;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CandidateRankingService {

    private final QuestionDataLoader dataLoader;
    private final PronunciationSimilarity similarity;
    private final PronunciationVariantGenerator variantGenerator;
    private final ContextScoringService contextScoringService;
    private final BadWordFilterService badWordFilterService;

    // 초기 실험값 (테스트 후 조정)
    private static final double MIN_SCORE = 0.60;
    private static final double MAX_GAP = 0.22;
    private static final int MAX_CANDIDATES = 3;

    // F2-2 초기 실험값
    private static final double SECOND_PRON_WEIGHT = 0.60;
    private static final double SECOND_CONTEXT_WEIGHT = 0.25;
    private static final double SECOND_SOURCE_WEIGHT = 0.15;

    // 발음이 지나치게 다른 단어 제외
    private static final double SECOND_MIN_PRON_SCORE = 0.45;

    // 최종 점수 하한
    private static final double SECOND_MIN_SCORE = 0.50;

    // 조사 제거형이 일반 유사도에서 과도하게 우대되지 않도록 감점
    private static final double STRIPPED_PENALTY = 0.02;

    // 규칙 우선순위별 감점
    private static final double HIGH_PENALTY = 0.03;
    private static final double MEDIUM_PENALTY = 0.06;
    private static final double LOW_PENALTY = 0.10;

    // F2 복합 발음 규칙 평가 제한
    private static final int MAX_COMPOUND_BASE_VARIANTS = 40;

    // 일치 유형: 숫자가 높을수록 정렬 우선
    private enum MatchType {
        SIMILARITY(1),
        RULE_VARIANT(2),
        CSV_PRONUNCIATION(3),
        STRIPPED_EXACT(4),
        EXACT_WORD(5);

        private final int rank;

        MatchType(int rank) {
            this.rank = rank;
        }
    }

    private record MatchResult(
            double score,
            MatchType type
    ) {}

    private record ScoredWord(
            QuestionWordData data,
            MatchResult match
    ) {}

    // F2-2 후보별 점수
    private record SecondScoredWord(
            QuestionWordData data,
            double pronunciationScore,
            double contextScore,
            double sourceScore,
            double finalScore
    ) {}

    public List<WordCandidate> rankFirst(
            List<String> targetExpressions
    ) {
        List<String> expressions = normalizeExpressions(
                targetExpressions
        );

        if (expressions.isEmpty()) {
            return List.of();
        }

        List<ScoredWord> scored = new ArrayList<>();

        for (QuestionWordData data : dataLoader.getWords()) {
            if (data.word() == null || data.word().isBlank()) {
                continue;
            }

            if (badWordFilterService.containsBlockedText(data.word())
                    || badWordFilterService.containsBlockedText(
                    data.definition()
            )) {
                continue;
            }

            MatchResult match = calculateBestMatch(
                    expressions, data, true
            );

            if (match.score() >= MIN_SCORE) {
                scored.add(new ScoredWord(data, match));
            }
        }

        // 점수 -> 일치 유형 -> 단어명 순으로 정렬 - 정확 일치 후보는 일반 유사도보다 우선
        Comparator<ScoredWord> comparator =
                Comparator.comparingInt(
                                (ScoredWord s) -> s.match().type().rank
                        ).reversed()
                        .thenComparing(
                                Comparator.comparingDouble(
                                        (ScoredWord s) -> s.match().score()
                                ).reversed()
                        )
                        .thenComparing(s -> s.data().word());

        // CSV 중복 단어 제거
        Map<String, ScoredWord> unique = new LinkedHashMap<>();

        scored.stream()
                .sorted(comparator)
                .forEach(item ->
                        unique.putIfAbsent(
                                item.data().word(), item
                        )
                );

        List<ScoredWord> sorted =
                new ArrayList<>(unique.values());

        if (sorted.isEmpty()) {
            return List.of();
        }

        // 일반 유사도 후보끼리는 최고 점수와의 차이 적용
        double topSimilarityScore = sorted.stream()
                .filter(s -> s.match().type()
                        == MatchType.SIMILARITY)
                .mapToDouble(s -> s.match().score())
                .max()
                .orElse(0.0);

        return sorted.stream()
                .filter(item -> {
                    // 명시적 일치 후보는 점수 차이로 제거하지 않음
                    if (item.match().type()
                            != MatchType.SIMILARITY) {
                        return true;
                    }

                    return topSimilarityScore
                            - item.match().score() <= MAX_GAP;
                })
                .limit(MAX_CANDIDATES)
                .map(this::toCandidate)
                .toList();
    }

    /**
     * F2-2: 1차 후보를 모두 거절했을 때 재정렬
     *
     * @param targetExpressions F1에서 추출한 표현
     * @param originalText 기존 STT 원문
     * @param heardFrom 아이가 선택한 출처
     * @param excludedKeys F2-1에서 실제 표시한 후보 key
     */
    public List<WordCandidate> rankSecond(
            List<String> targetExpressions,
            String originalText,
            HeardFrom heardFrom,
            Set<String> excludedKeys
    ) {
        List<String> expressions = normalizeExpressions(targetExpressions);

        if (expressions.isEmpty()) {
            return List.of();
        }

        Set<String> excluded = excludedKeys == null
                ? Set.of()
                : excludedKeys;

        boolean hasContext = originalText != null
                && !originalText.isBlank();

        boolean hasSource = heardFrom != null
                && heardFrom != HeardFrom.UNKNOWN;

        List<SecondScoredWord> scored = new ArrayList<>();

        for (QuestionWordData data : dataLoader.getWords()) {

            if (data.word() == null || data.word().isBlank()) {
                continue;
            }

            if (badWordFilterService.containsBlockedText(data.word())
                    || badWordFilterService.containsBlockedText(
                    data.definition()
            )) {
                continue;
            }

            String wordKey = data.word()
                    .trim()
                    .toLowerCase(Locale.ROOT);

            boolean alreadyShown = excluded.stream()
                    .filter(Objects::nonNull)
                    .map(value ->
                            value.trim().toLowerCase(Locale.ROOT)
                    )
                    .anyMatch(value ->
                            value.equals(wordKey)
                                    || value.endsWith(":" + wordKey)
                    );

            if (alreadyShown) {
                continue;
            }

            MatchResult match = calculateBestMatch(
                    expressions, data, false
            );
            double pronunciationScore = match.score();

            if (pronunciationScore < SECOND_MIN_PRON_SCORE) {
                continue;
            }

            double contextScore = hasContext
                    ? contextScoringService.calculateContextScore(
                    originalText, data
            )
                    : 0.0;

            double sourceScore = hasSource
                    ? contextScoringService.calculateSourceScore(
                    heardFrom, data
            )
                    : 0.0;

            double finalScore = calculateSecondPassScore(
                    pronunciationScore,
                    hasContext ? contextScore : null,
                    hasSource ? sourceScore : null
            );

            if (finalScore < SECOND_MIN_SCORE) {
                continue;
            }

            scored.add(new SecondScoredWord(
                    data,
                    pronunciationScore,
                    contextScore,
                    sourceScore,
                    finalScore
            ));
        }

        Comparator<SecondScoredWord> comparator =
                Comparator.comparingDouble(
                                SecondScoredWord::finalScore
                        ).reversed()
                        .thenComparing(
                                Comparator.comparingDouble(
                                        SecondScoredWord::pronunciationScore
                                ).reversed()
                        )
                        .thenComparing(item -> item.data().word());

        Map<String, SecondScoredWord> unique = new LinkedHashMap<>();

        scored.stream()
                .sorted(comparator)
                .forEach(item ->
                        unique.putIfAbsent(
                                item.data().word(), item
                        )
                );

        return unique.values().stream()
                .limit(MAX_CANDIDATES)
                .map(this::toSecondCandidate)
                .toList();
    }

    // 기존 호출부 호환
    public double calculateExternalPronunciationScore(
            List<String> targetExpressions,
            String word
    ) {
        return calculateExternalPronunciationScore(
                targetExpressions, word, true
        );
    }

    // F1/F2 공통 외부 단어 발음 평가
    public double calculateExternalPronunciationScore(
            List<String> targetExpressions,
            String word,
            boolean isFirstPass
    ) {
        if (word == null || word.isBlank()) {
            return 0.0;
        }

        List<String> expressions =
                normalizeExpressions(targetExpressions);

        if (expressions.isEmpty()) {
            return 0.0;
        }

        word = word.trim();

        return calculateExternalPronunciationScore(
                targetExpressions,
                new QuestionWordData(
                        word,
                        word,
                        null,
                        null,
                        null,
                        null,
                        null,
                        word,
                        word,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                isFirstPass
        );
    }

    // CSV 원본 데이터(발음 필드 포함) 기반 평가
    public double calculateExternalPronunciationScore(
            List<String> targetExpressions,
            QuestionWordData data,
            boolean isFirstPass
    ) {
        if (data == null || data.word() == null || data.word().isBlank()) {
            return 0.0;
        }

        List<String> expressions =
                normalizeExpressions(targetExpressions);

        if (expressions.isEmpty()) {
            return 0.0;
        }

        String normalizedWord = data.word().trim();

        QuestionWordData normalizedData = new QuestionWordData(
                normalizedWord,
                data.matchKey(),
                data.category(),
                data.heardFrom(),
                data.heardNote(),
                data.hearsExample(),
                data.pronPoint(),
                data.pronExpected(),
                data.pronActualAge3(),
                data.confusableWord(),
                data.definition(),
                data.hint(),
                data.descAge3To4(),
                data.descAge5(),
                data.example()
        );

        return calculateBestMatch(
                expressions,
                normalizedData,
                isFirstPass
        ).score();
    }

    public boolean containsCsvWord(String word) {
        if (word == null || word.isBlank()) {
            return false;
        }

        String normalized = word.trim();
        return dataLoader.getWords().stream()
                .map(QuestionWordData::word)
                .filter(Objects::nonNull)
                .map(String::trim)
                .anyMatch(candidate -> candidate.equalsIgnoreCase(normalized));
    }

    public List<QuestionWordData> getWords() {
        return List.copyOf(dataLoader.getWords());
    }

    public double calculateSecondPassScore(
            double pronunciationScore,
            Double contextScore,
            Double sourceScore
    ) {
        double weightedScore =
                clamp(pronunciationScore)
                        * SECOND_PRON_WEIGHT;

        double totalWeight = SECOND_PRON_WEIGHT;

        if (contextScore != null) {

            weightedScore +=
                    clamp(contextScore)
                            * SECOND_CONTEXT_WEIGHT;

            totalWeight += SECOND_CONTEXT_WEIGHT;
        }

        if (sourceScore != null) {

            weightedScore +=
                    clamp(sourceScore)
                            * SECOND_SOURCE_WEIGHT;

            totalWeight += SECOND_SOURCE_WEIGHT;
        }

        return clamp(weightedScore / totalWeight);
    }

    // 원본과 조사 제거형 정리
    private List<String> normalizeExpressions(
            List<String> targetExpressions
    ) {
        if (targetExpressions == null) {
            return List.of();
        }

        return targetExpressions.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private MatchResult calculateBestMatch(
            List<String> expressions,
            QuestionWordData data,
            boolean isFirstPass
    ) {
        String word = data.word().trim();

        // 1. 원본이 사전 단어와 정확히 일치
        if (expressions.get(0).equals(word)) {
            return new MatchResult(
                    1.0, MatchType.EXACT_WORD
            );
        }

        // 2. 조사 제거형이 사전 단어와 정확히 일치
        if (expressions.stream()
                .skip(1)
                .anyMatch(word::equals)) {
            return new MatchResult(
                    1.0, MatchType.STRIPPED_EXACT
            );
        }

        Set<String> csvPronunciations =
                new LinkedHashSet<>();

        addPronunciations(
                csvPronunciations, data.pronExpected()
        );

        addPronunciations(
                csvPronunciations, data.pronActualAge3()
        );

        Set<String> basePronunciations =
                new LinkedHashSet<>();

        basePronunciations.add(word);
        basePronunciations.addAll(csvPronunciations);

        MatchResult best = new MatchResult(
                0.0, MatchType.SIMILARITY
        );

        // 3. 사전 표기 및 CSV 발음과 비교
        for (int i = 0; i < expressions.size(); i++) {

            String expression = expressions.get(i);

            double penalty = i == 0
                    ? 0.0 : STRIPPED_PENALTY;

            for (String pronunciation : basePronunciations) {

                double score = similarity.calculate(
                        expression, pronunciation
                );

                MatchType type =
                        csvPronunciations.contains(pronunciation)
                                && expression.equals(pronunciation)
                                ? MatchType.CSV_PRONUNCIATION
                                : MatchType.SIMILARITY;

                double adjusted = clamp(score - penalty);

                best = better(
                        best,
                        new MatchResult(adjusted, type)
                );
            }
        }

        // 4. 단일 발음 규칙 변형
        Map<String, Double> variants =
                new LinkedHashMap<>();

        for (String pronunciation : basePronunciations) {

            for (PronunciationVariantGenerator.PronunciationVariant variant
                    : variantGenerator.generateDetailed(pronunciation)) {

                double penalty = rulePenalty(
                        variant.priority()
                );

                variants.merge(
                        variant.pronunciation(),
                        penalty,
                        Math::min
                );
            }
        }

        basePronunciations.forEach(variants::remove);

        // 단일 규칙 발음 + 자모 유사도 평가
        for (int i = 0; i < expressions.size(); i++) {

            String expression = expressions.get(i);

            double expressionPenalty = i == 0
                    ? 0.0 : STRIPPED_PENALTY;

            for (Map.Entry<String, Double> entry
                    : variants.entrySet()) {

                double score = similarity.calculate(
                        expression, entry.getKey()
                );

                double adjusted = clamp(
                        score
                                - expressionPenalty
                                - entry.getValue()
                );

                MatchType type =
                        expression.equals(entry.getKey())
                                ? MatchType.RULE_VARIANT
                                : MatchType.SIMILARITY;

                best = better(
                        best,
                        new MatchResult(adjusted, type)
                );
            }
        }

        // 5. F2에서만 복합 발음 규칙 평가
        if (!isFirstPass) {

            best = evaluateCompoundVariants(
                    expressions,
                    variants,
                    best
            );
        }

        return best;
    }

    private MatchResult evaluateCompoundVariants(
            List<String> expressions,
            Map<String, Double> firstVariants,
            MatchResult currentBest
    ) {
        MatchResult best = currentBest;

        // 감점이 적은 변형부터 추가 탐색
        List<Map.Entry<String, Double>> sortedVariants =
                firstVariants.entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByValue())
                        .limit(MAX_COMPOUND_BASE_VARIANTS)
                        .toList();

        for (Map.Entry<String, Double> first
                : sortedVariants) {

            List<PronunciationVariantGenerator.PronunciationVariant>
                    secondVariants =
                    variantGenerator.generateDetailed(
                            first.getKey()
                    );

            for (var second : secondVariants) {

                double totalPenalty =
                        first.getValue()
                                + rulePenalty(second.priority());

                for (int i = 0; i < expressions.size(); i++) {

                    String expression = expressions.get(i);

                    // 복합 규칙은 정확하게 일치할 때만 인정
                    if (!expression.equals(
                            second.pronunciation()
                    )) {
                        continue;
                    }

                    double expressionPenalty = i == 0
                            ? 0.0 : STRIPPED_PENALTY;

                    double adjusted = clamp(
                            1.0
                                    - totalPenalty
                                    - expressionPenalty
                    );

                    best = better(
                            best,
                            new MatchResult(
                                    adjusted,
                                    MatchType.RULE_VARIANT
                            )
                    );
                }
            }
        }

        return best;
    }

    // 두 결과 중 우선순위가 높은 결과 선택
    private MatchResult better(
            MatchResult current,
            MatchResult candidate
    ) {
        // 동일 단어 내에서는 점수가 높은 결과 우선
        if (candidate.score() > current.score() + 1e-9) {
            return candidate;
        }

        // 점수가 같다면 일치 유형으로 결정
        if (Math.abs(candidate.score() - current.score()) < 1e-9
                && candidate.type().rank > current.type().rank) {
            return candidate;
        }

        return current;
    }

    // JSON priority에 따른 감점
    private double rulePenalty(String priority) {
        if (priority == null) {
            return MEDIUM_PENALTY;
        }

        return switch (priority.toUpperCase(Locale.ROOT)) {
            case "HIGH" -> HIGH_PENALTY;
            case "LOW" -> LOW_PENALTY;
            default -> MEDIUM_PENALTY;
        };
    }

    // CSV 발음 칸: 쉼표, 세미콜론, | 지원
    private void addPronunciations(
            Set<String> pronunciations,
            String raw
    ) {
        if (raw == null || raw.isBlank()) {
            return;
        }

        Arrays.stream(raw.split("[,;|]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(pronunciations::add);
    }

    private WordCandidate toCandidate(ScoredWord scored) {
        QuestionWordData data = scored.data();
        double score = scored.match().score();

        return new WordCandidate(
                "CURATED:" + data.word(),
                null,
                data.word(),
                WordSourceType.CURATED,
                score,
                0.0, // F2-2에서 문맥 점수 연결
                score,
                data.definition(),
                null,
                null,
                null,
                null
        );
    }

    // F2-2 결과 -> 기존 WordCandidate 형식
    private WordCandidate toSecondCandidate(
            SecondScoredWord scored
    ) {
        QuestionWordData data = scored.data();

        double contextWeight = SECOND_CONTEXT_WEIGHT;
        double sourceWeight = SECOND_SOURCE_WEIGHT;

        double combinedContextScore = clamp(
                (
                        scored.contextScore() * contextWeight
                                + scored.sourceScore() * sourceWeight
                ) / (contextWeight + sourceWeight)
        );

        return new WordCandidate(
                "CURATED:" + data.word(),
                null,
                data.word(),
                WordSourceType.CURATED,
                scored.pronunciationScore(),
                combinedContextScore,
                scored.finalScore(),
                data.definition(),
                null,
                null,
                null,
                null
        );
    }

    private double clamp(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }
}
