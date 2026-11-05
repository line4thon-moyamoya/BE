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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidateRankingService {

    private final QuestionDataLoader dataLoader;
    private final PronunciationSimilarity similarity;
    private final PronunciationVariantGenerator variantGenerator;
    private final ContextScoringService contextScoringService;

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

    /**
     * 119개 단어에서 1차 후보 0~3개 선정
     * targetExpressions[0] = 원본
     * targetExpressions[1...] = 조사 제거형
     */
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

            MatchResult match = calculateBestMatch(
                    expressions, data
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

        double pronWeight = SECOND_PRON_WEIGHT;
        double contextWeight = hasContext
                ? SECOND_CONTEXT_WEIGHT : 0.0;
        double sourceWeight = hasSource
                ? SECOND_SOURCE_WEIGHT : 0.0;

        double totalWeight =
                pronWeight + contextWeight + sourceWeight;

        List<SecondScoredWord> scored = new ArrayList<>();

        for (QuestionWordData data : dataLoader.getWords()) {

            if (data.word() == null || data.word().isBlank()) {
                continue;
            }

            String candidateKey = "CURATED:" + data.word();

            if (excluded.contains(candidateKey)) {
                continue;
            }

            MatchResult match = calculateBestMatch(expressions, data);
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

            double finalScore = clamp(
                    (
                            pronunciationScore * pronWeight
                                    + contextScore * contextWeight
                                    + sourceScore * sourceWeight
                    ) / totalWeight
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

    /**
     * 단어별 최고 일치 결과 계산
     */
    private MatchResult calculateBestMatch(
            List<String> expressions,
            QuestionWordData data
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

        // 원본 표기와 CSV 발음 별칭을 구분
        Set<String> csvPronunciations =
                new LinkedHashSet<>();

        addPronunciations(
                csvPronunciations, data.pronExpected()
        );
        addPronunciations(
                csvPronunciations, data.pronActualAge3()
        );

        // 기본 표기 및 CSV 발음을 규칙 생성의 기반으로 사용
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

                // 정확 CSV 발음은 일치 유형으로 보호
                double adjusted = clamp(score - penalty);

                best = better(
                        best,
                        new MatchResult(adjusted, type)
                );
            }
        }

        // 4. JSON 규칙 변형 발음과 비교
        Map<String, Double> variants = new LinkedHashMap<>();

        for (String pronunciation : basePronunciations) {
            for (PronunciationVariantGenerator.PronunciationVariant variant
                    : variantGenerator.generateDetailed(pronunciation)) {

                double penalty = rulePenalty(
                        variant.priority()
                );

                // 같은 발음이 여러 경로에서 나오면 낮은 감점 유지
                variants.merge(
                        variant.pronunciation(),
                        penalty,
                        Math::min
                );
            }
        }

        // 기존 표기와 CSV 발음은 규칙 변형에서 제외
        basePronunciations.forEach(variants::remove);

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
                        score - expressionPenalty - entry.getValue()
                );

                // 규칙 발음과 정확히 일치한 경우만 RULE_VARIANT
                MatchType type = expression.equals(entry.getKey())
                        ? MatchType.RULE_VARIANT
                        : MatchType.SIMILARITY;

                best = better(
                        best,
                        new MatchResult(adjusted, type)
                );
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

    /**
     * 조사 제거형을 질문 기록에 반영할 수 있는지 판단
     * 정확히 하나의 사전 단어만 일치할 때 선택
     */
    public Optional<String> resolveTargetPronunciation(
            List<String> targetExpressions
    ) {
        List<String> expressions = normalizeExpressions(
                targetExpressions
        );

        if (expressions.isEmpty()) {
            return Optional.empty();
        }

        String original = expressions.get(0);

        Set<String> dictionaryWords = dataLoader.getWords()
                .stream()
                .map(QuestionWordData::word)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (dictionaryWords.contains(original)) {
            return Optional.of(original);
        }

        List<String> matches = expressions.stream()
                .skip(1)
                .filter(dictionaryWords::contains)
                .distinct()
                .toList();

        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }

        return Optional.empty();
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
                null
        );
    }

    private double clamp(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }
}
