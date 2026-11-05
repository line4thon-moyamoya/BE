// 119개 단어 대상으로 1차 후보 1~3개 선정
package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.config.QuestionDataLoader;
import com.moyamoya.domain.question.model.QuestionWordData;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.WordSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CandidateRankingService {

    private final QuestionDataLoader dataLoader;
    private final PronunciationSimilarity similarity;
    private final PronunciationVariantGenerator variantGenerator;

    // 초기 실험값: 테스트 후 조정할 예정
    private static final double MIN_SCORE = 0.60;
    private static final double MAX_GAP = 0.22;
    private static final int MAX_CANDIDATES = 3;

    // 변형 발음이 원본과 일치하는 것보다 우대되지 않도록 감점
    private static final double RULE_PENALTY = 0.05;

    // 조사 제거형은 원본 표현보다 약간 낮은 우선순위
    private static final double STRIPPED_EXPRESSION_PENALTY = 0.02;

    /**
     * 119개 단어 후보 중에서 점수가 충분한 후보 0~3개를 선정
     * @param targetExpressions F2가 생성한 표현들 (원본이 첫 번째)
     * @return 점수가 충분한 후보 0~3개
     */
    public List<WordCandidate> rankFirst(
            List<String> targetExpressions
    ) {
        if (targetExpressions == null || targetExpressions.isEmpty()) {
            return List.of();
        }

        List<String> expressions = targetExpressions.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        if (expressions.isEmpty()) {
            return List.of();
        }

        List<ScoredWord> scored = new ArrayList<>();

        for (QuestionWordData wordData : dataLoader.getWords()) {
            if (wordData.word() == null || wordData.word().isBlank()) {
                continue;
            }

            double score = calculateBestScore(expressions, wordData);

            if (score >= MIN_SCORE) {
                scored.add(new ScoredWord(wordData, score));
            }
        }

        // 같은 단어가 CSV에 중복되어 있어도 카드 하나만 생성
        Map<String, ScoredWord> unique = new LinkedHashMap<>();

        scored.stream()
                .sorted(
                        Comparator.comparingDouble(ScoredWord::score)
                                .reversed()
                                .thenComparing(s -> s.data().word())
                )
                .forEach(item ->
                        unique.putIfAbsent(item.data().word(), item)
                );

        List<ScoredWord> sorted = new ArrayList<>(unique.values());

        if (sorted.isEmpty()) {
            return List.of();
        }

        double topScore = sorted.get(0).score();

        return sorted.stream()
                .filter(item -> topScore - item.score() <= MAX_GAP)
                .limit(MAX_CANDIDATES)
                .map(this::toCandidate)
                .toList();
    }

    // 조사 제거해도 된다고 판단되면 targetPronunciation만 갱신 => 질문 기록, 물어볼 단어에 저장
    // 조사 제거형을 안전하게 선택할 수 있는지 확인
    public Optional<String> resolveTargetPronunciation(
            List<String> targetExpressions
    ) {
        if (targetExpressions == null || targetExpressions.isEmpty()) {
            return Optional.empty();
        }

        String original = targetExpressions.get(0);

        // CSV에 존재하는 단어 목록
        Set<String> dictionaryWords = dataLoader.getWords().stream()
                .map(QuestionWordData::word)
                .collect(Collectors.toSet());

        // 원본 자체가 단어라면 제거하지 않음
        if (dictionaryWords.contains(original)) {
            return Optional.of(original);
        }

        // 조사 제거형 중 정확히 일치하는 단어 찾기
        List<String> matches = targetExpressions.stream()
                .skip(1)
                .distinct()
                .filter(dictionaryWords::contains)
                .toList();

        // 정확히 하나일 때만 사용
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }

        // 모호하거나 일치하는 단어가 없으면 원본 유지
        return Optional.empty();
    }

    /**
     * 검색용 표현과 CSV 단어의 최고 발음 점수를 계산
     */
    private double calculateBestScore(
            List<String> expressions,
            QuestionWordData data
    ) {
        String word = data.word();

        // 원본 표기 + CSV 발음 별칭
        Set<String> pronunciations = new LinkedHashSet<>();
        pronunciations.add(word);

        addPronunciations(pronunciations, data.pronExpected());
        addPronunciations(pronunciations, data.pronActualAge3());

        // JSON 규칙으로 예상 발음 추가
        Set<String> ruleVariants = new LinkedHashSet<>();

        for (String pronunciation : pronunciations) {
            ruleVariants.addAll(
                    variantGenerator.generate(pronunciation)
            );
        }

        // 원래 존재하는 표기/발음은 규칙 변형으로 취급하지 않음
        ruleVariants.removeAll(pronunciations);

        double best = 0.0;

        for (int i = 0; i < expressions.size(); i++) {
            String expression = expressions.get(i);

            double expressionPenalty =
                    i == 0 ? 0.0 : STRIPPED_EXPRESSION_PENALTY;

            // 표기 또는 CSV 발음과 비교
            for (String pronunciation : pronunciations) {
                double score =
                        similarity.calculate(expression, pronunciation);

                best = Math.max(
                        best,
                        score - expressionPenalty
                );
            }

            // JSON 규칙으로 생성된 예상 발음과 비교
            for (String variant : ruleVariants) {
                double score =
                        similarity.calculate(expression, variant);

                best = Math.max(
                        best,
                        score - expressionPenalty - RULE_PENALTY
                );
            }
        }

        return clamp(best);
    }

    /**
     * CSV 발음 칸에서 복수 표현을 읽기 - 쉼표, 세미콜론, | 구분자를 지원
     */
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

        return new WordCandidate(
                "CURATED:" + data.word(),
                null,
                data.word(),
                WordSourceType.CURATED,
                scored.score(),
                0.0, // 추후 문맥 점수 연결시켜야 함
                scored.score(),
                data.definition(),
                null
        );
    }

    private double clamp(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }

    private record ScoredWord(
            QuestionWordData data,
            double score
    ) {}
}

