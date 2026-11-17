// 22개 발음 규칙 적용한 후보 생성기
package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PronunciationVariantGenerator {

    private static final int F1_QUERY_LIMIT = 10;
    private static final int F2_QUERY_LIMIT = 25;

    private static final int F2_EXPANSION_LIMIT = 40;

    private static final String COMMON_FINALS = "ㅇㄴㄹㅁㄱㅂㄷ";

    private static final int HANGUL_START = 0xAC00;
    private static final int HANGUL_END = 0xD7A3;

    private static final String INITIALS =
            "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ";

    private static final String MEDIALS =
            "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ";

    private static final String FINALS =
            "\0ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ";

    private final QuestionDataLoader dataLoader;

    public PronunciationVariantGenerator(
            QuestionDataLoader dataLoader
    ) {
        this.dataLoader = dataLoader;
    }

    // 원본 단어와 규칙을 한 번 적용한 예상 발음을 반환
    public List<String> generate(String word) {
        if (word == null || word.isBlank()) {
            return List.of();
        }

        String original = word.trim();
        Set<String> variants = new LinkedHashSet<>();
        variants.add(original);

        JsonNode rules = dataLoader.getPronunciationRules()
                .path("rules");

        for (JsonNode rule : rules) {
            if (!rule.path("enabled").asBoolean(false)) {
                continue;
            }

            applyRule(original, rule, variants);
        }

        return List.copyOf(variants);
    }

    // 생성된 발음과 적용 규칙 정보를 함께 반환
    public record PronunciationVariant(
            String pronunciation,
            String ruleId,
            String priority
    ) {}

    // 기존 호출부 호환
    public List<String> generateDictionaryQueries(String expression) {
        return generateDictionaryQueries(expression, true);
    }

    // 외부 사전 검색용 표현 생성
    public List<String> generateDictionaryQueries(
            String expression,
            boolean isFirstPass
    ) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }

        String original = expression.trim();

        int limit = isFirstPass
                ? F1_QUERY_LIMIT
                : F2_QUERY_LIMIT;

        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(original);

        List<PronunciationVariant> reverseVariants =
                diversifyVariants(
                        generateReverseDetailed(original)
                );

        List<PronunciationVariant> forwardVariants =
                diversifyVariants(
                        generateDetailed(original)
                );

        // F1은 단일 변환 중심
        int reverseLimit = isFirstPass
                ? limit - 3
                : limit / 2;

        reverseVariants.stream()
                .limit(reverseLimit)
                .map(PronunciationVariant::pronunciation)
                .forEach(queries::add);

        // F2는 복합 역방향 변환 추가
        if (!isFirstPass) {
            generateCombinedReverseVariants(
                    original,
                    reverseVariants
            ).stream()
                    .limit(8)
                    .map(PronunciationVariant::pronunciation)
                    .forEach(queries::add);
        }

        // 기존 정방향 탐색도 유지
        forwardVariants.stream()
                .sorted(Comparator.comparingInt(
                        (PronunciationVariant variant) ->
                                priorityRank(variant.priority())
                ).reversed())
                .map(PronunciationVariant::pronunciation)
                .forEach(queries::add);

        // 중복 등으로 남은 검색 공간 채우기
        reverseVariants.stream()
                .map(PronunciationVariant::pronunciation)
                .forEach(queries::add);

        return queries.stream()
                .limit(limit)
                .toList();
    }

    // 규칙별 예상 발음 생성 (원본은 포함하지 않음)
    public List<PronunciationVariant> generateDetailed(String word) {
        if (word == null || word.isBlank()) {
            return List.of();
        }

        String original = word.trim();
        JsonNode rules = dataLoader.getPronunciationRules()
                .path("rules");

        // 같은 발음이 여러 규칙으로 생성되면 우선순위가 높은 규칙 유지
        Map<String, PronunciationVariant> results =
                new LinkedHashMap<>();

        for (JsonNode rule : rules) {
            if (!rule.path("enabled").asBoolean(false)) {
                continue;
            }

            String ruleId = rule.path("id").asText("");
            String priority = rule.path("priority")
                    .asText("MEDIUM")
                    .toUpperCase(Locale.ROOT);

            // 기존 발음 변형 로직 재사용
            Set<String> generated = new LinkedHashSet<>();
            applyRule(original, rule, generated);

            for (String pronunciation : generated) {
                if (pronunciation.equals(original)) {
                    continue;
                }

                PronunciationVariant candidate =
                        new PronunciationVariant(
                                pronunciation,
                                ruleId,
                                priority
                        );

                PronunciationVariant previous =
                        results.get(pronunciation);

                if (previous == null ||
                        priorityRank(priority)
                                > priorityRank(previous.priority())) {
                    results.put(pronunciation, candidate);
                }
            }
        }

        return List.copyOf(results.values());
    }

    public List<PronunciationVariant> generateReverseDetailed(
            String expression
    ) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }

        String original = expression.trim();

        Map<String, PronunciationVariant> results =
                new LinkedHashMap<>();

        JsonNode rules = dataLoader.getPronunciationRules()
                .path("rules");

        for (JsonNode rule : rules) {
            if (!rule.path("enabled").asBoolean(false)) {
                continue;
            }

            String ruleId = rule.path("id").asText();
            String priority = rule.path("priority")
                    .asText("MEDIUM")
                    .toUpperCase(Locale.ROOT);

            Set<String> generated = new LinkedHashSet<>();

            applyReverseRule(original, rule, generated);

            for (String pronunciation : generated) {
                if (original.equals(pronunciation)) {
                    continue;
                }

                PronunciationVariant candidate =
                        new PronunciationVariant(
                                pronunciation,
                                ruleId,
                                priority
                        );

                results.merge(
                        pronunciation,
                        candidate,
                        (previous, current) ->
                                priorityRank(current.priority())
                                        > priorityRank(previous.priority())
                                        ? current
                                        : previous
                );
            }
        }

        return results.values().stream()
                .sorted(Comparator.comparingInt(
                        (PronunciationVariant variant) ->
                                priorityRank(variant.priority())
                ).reversed())
                .toList();
    }

    private void applyReverseRule(
            String word,
            JsonNode rule,
            Set<String> variants
    ) {
        String type = rule.path("type").asText();
        String position = rule.path("position").asText();

        List<String> from = readStrings(rule.path("from"));
        List<String> to = readStrings(rule.path("to"));

        List<String> afterInitials =
                readStrings(rule.path("afterInitials"));

        for (int i = 0; i < word.length(); i++) {

            char syllable = word.charAt(i);

            if (!isHangul(syllable)) {
                continue;
            }

            int[] parts = decompose(syllable);

            int initial = parts[0];
            int medial = parts[1];
            int fin = parts[2];

            switch (type) {

                case "INITIAL_SUBSTITUTION" -> {
                    if (!matchesPosition(position, i)) {
                        continue;
                    }

                    reverseComponent(
                            word, i, parts,
                            INITIALS, initial,
                            from, to, 0, variants
                    );
                }

                case "VOWEL_SUBSTITUTION" -> {
                    reverseComponent(
                            word, i, parts,
                            MEDIALS, medial,
                            from, to, 1, variants
                    );
                }

                case "PALATALIZED_VOWEL" -> {

                    String currentInitial =
                            String.valueOf(
                                    INITIALS.charAt(initial)
                            );

                    if (!afterInitials.contains(currentInitial)) {
                        continue;
                    }

                    reverseComponent(
                            word, i, parts,
                            MEDIALS, medial,
                            from, to, 1, variants
                    );
                }

                case "FINAL_CONSONANT_SUBSTITUTION" -> {
                    reverseComponent(
                            word, i, parts,
                            FINALS, fin,
                            from, to, 2, variants
                    );
                }

                case "FINAL_CONSONANT_DELETION" -> {

                    // 받침 없는 음절에 받침 복원
                    if (fin != 0) {
                        continue;
                    }

                    for (char restored :
                            COMMON_FINALS.toCharArray()) {

                        int restoredIndex =
                                FINALS.indexOf(restored);

                        if (restoredIndex >= 0) {
                            addReverseVariant(
                                    word, i, parts,
                                    2, restoredIndex,
                                    variants
                            );
                        }
                    }
                }

                case "CONSONANT_DELETION" -> {

                    if (!matchesPosition(position, i)) {
                        continue;
                    }

                    // 초성 ㅇ으로 발음된 경우 원래 초성 복원
                    if (INITIALS.charAt(initial) != 'ㅇ') {
                        continue;
                    }

                    for (String value : from) {

                        int restoredIndex =
                                INITIALS.indexOf(value);

                        if (!value.isBlank()
                                && restoredIndex >= 0) {
                            addReverseVariant(
                                    word, i, parts,
                                    0, restoredIndex,
                                    variants
                            );
                        }
                    }
                }

                default -> {
                    // 미구현 규칙 제외
                }
            }
        }
    }

    // 복합 역방향 변환, 최대 두 번 규칙 허용(무한히 재귀적으로 변형하지 않도록 제한)
    // 12개의 기본 역방향 변형에 대해 복합 규칙을 평가 => 중복 제거와 분산 정렬 => 최대 40개 제한
    private List<PronunciationVariant> generateCombinedReverseVariants(
            String original,
            List<PronunciationVariant> firstVariants
    ) {
        Map<String, PronunciationVariant> results =
                new LinkedHashMap<>();

        Set<String> singleVariantWords =
                new HashSet<>();

        singleVariantWords.add(original);

        firstVariants.stream()
                .map(PronunciationVariant::pronunciation)
                .forEach(singleVariantWords::add);

        List<PronunciationVariant> baseVariants =
                diversifyVariants(firstVariants)
                        .stream()
                        .limit(12)
                        .toList();

        for (PronunciationVariant first : baseVariants) {

            List<PronunciationVariant> secondVariants =
                    diversifyVariants(
                            generateReverseDetailed(
                                    first.pronunciation()
                            )
                    );

            for (PronunciationVariant second : secondVariants) {

                String pronunciation =
                        second.pronunciation();

                // 원본 및 단일 변형과 중복되는 결과 제외
                if (singleVariantWords.contains(pronunciation)) {
                    continue;
                }

                String priority =
                        priorityRank(first.priority())
                                <= priorityRank(second.priority())
                                ? first.priority()
                                : second.priority();

                PronunciationVariant combined =
                        new PronunciationVariant(
                                pronunciation,
                                first.ruleId()
                                        + "+"
                                        + second.ruleId(),
                                priority
                        );

                results.merge(
                        pronunciation,
                        combined,
                        (previous, current) ->
                                priorityRank(current.priority())
                                        > priorityRank(previous.priority())
                                        ? current
                                        : previous
                );
            }
        }

        return diversifyVariants(
                new ArrayList<>(results.values())
        ).stream()
                .limit(F2_EXPANSION_LIMIT)
                .toList();
    }

    private void reverseComponent(
            String word,
            int syllableIndex,
            int[] parts,
            String components,
            int currentIndex,
            List<String> from,
            List<String> to,
            int componentIndex,
            Set<String> variants
    ) {
        String current = String.valueOf(
                components.charAt(currentIndex)
        );

        // 일대일 대응 규칙
        if (from.size() == to.size()) {

            for (int i = 0; i < to.size(); i++) {

                if (!current.equals(to.get(i))) {
                    continue;
                }

                restoreComponent(
                        word, syllableIndex, parts,
                        components, from.get(i),
                        componentIndex, variants
                );
            }

            return;
        }

        // 다대일 대응 규칙
        if (!to.contains(current)) {
            return;
        }

        for (String restored : from) {

            restoreComponent(
                    word, syllableIndex, parts,
                    components, restored,
                    componentIndex, variants
            );
        }
    }

    private void restoreComponent(
            String word,
            int syllableIndex,
            int[] parts,
            String components,
            String restored,
            int componentIndex,
            Set<String> variants
    ) {
        if (restored == null || restored.isEmpty()) {
            return;
        }

        int restoredIndex =
                components.indexOf(restored);

        if (restoredIndex < 0) {
            return;
        }

        addReverseVariant(
                word, syllableIndex, parts,
                componentIndex, restoredIndex,
                variants
        );
    }

    private void addReverseVariant(
            String word,
            int syllableIndex,
            int[] parts,
            int componentIndex,
            int restoredIndex,
            Set<String> variants
    ) {
        int[] changed = parts.clone();

        changed[componentIndex] = restoredIndex;

        char restored = (char) (
                HANGUL_START
                        + changed[0] * 21 * 28
                        + changed[1] * 28
                        + changed[2]
        );

        String result = word.substring(0, syllableIndex)
                + restored
                + word.substring(syllableIndex + 1);

        if (!result.equals(word)) {
            variants.add(result);
        }
    }

    private int priorityRank(String priority) {
        return switch (priority) {
            case "HIGH" -> 3;
            case "MEDIUM" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    private void applyRule(
            String word,
            JsonNode rule,
            Set<String> variants
    ) {
        String type = rule.path("type").asText();
        String position = rule.path("position").asText();

        List<String> from = readStrings(rule.path("from"));
        List<String> to = readStrings(rule.path("to"));
        List<String> afterInitials =
                readStrings(rule.path("afterInitials"));

        for (int i = 0; i < word.length(); i++) {
            char syllable = word.charAt(i);

            if (!isHangul(syllable)) {
                continue;
            }

            int[] parts = decompose(syllable);

            int initial = parts[0];
            int medial = parts[1];
            int fin = parts[2];

            switch (type) {

                case "INITIAL_SUBSTITUTION" -> {
                    if (!matchesPosition(position, i)) {
                        continue;
                    }

                    replaceComponent(
                            word, i, parts,
                            INITIALS, initial,
                            from, to, 0, variants
                    );
                }

                case "VOWEL_SUBSTITUTION" -> {
                    replaceComponent(
                            word, i, parts,
                            MEDIALS, medial,
                            from, to, 1, variants
                    );
                }

                case "PALATALIZED_VOWEL" -> {
                    String currentInitial =
                            String.valueOf(INITIALS.charAt(initial));

                    if (!afterInitials.contains(currentInitial)) {
                        continue;
                    }

                    replaceComponent(
                            word, i, parts,
                            MEDIALS, medial,
                            from, to, 1, variants
                    );
                }

                case "FINAL_CONSONANT_SUBSTITUTION" -> {
                    if (fin == 0) {
                        continue;
                    }

                    replaceComponent(
                            word, i, parts,
                            FINALS, fin,
                            from, to, 2, variants
                    );
                }

                case "FINAL_CONSONANT_DELETION" -> {
                    if (fin == 0) {
                        continue;
                    }

                    // 받침만 제거하고 음절은 유지
                    addVariant(
                            word, i,
                            compose(initial, medial, 0),
                            variants
                    );
                }

                case "CONSONANT_DELETION" -> {
                    if (!matchesPosition(position, i)) {
                        continue;
                    }

                    String currentInitial =
                            String.valueOf(INITIALS.charAt(initial));

                    if (!from.contains(currentInitial)) {
                        continue;
                    }

                    // 초성 삭제는 초성 ㅇ으로 바꾸어 음절 유지
                    int silentInitial = INITIALS.indexOf('ㅇ');

                    addVariant(
                            word, i,
                            compose(silentInitial, medial, fin),
                            variants
                    );
                }

                default -> {
                    // 비활성 규칙 및 미지원 타입은 변환하지 않음
                }
            }
        }
    }

    // 자모를 변경한 음절을 생성
    private void replaceComponent(
            String word,
            int index,
            int[] parts,
            String alphabet,
            int currentIndex,
            List<String> from,
            List<String> to,
            int component,
            Set<String> variants
    ) {
        String current =
                String.valueOf(alphabet.charAt(currentIndex));

        for (int j = 0; j < from.size(); j++) {
            if (!from.get(j).equals(current)) {
                continue;
            }

            // 배열 길이가 같으면 1:1 대응
            // 길이가 다르면 각 to 값을 가능한 변형으로 사용
            List<String> replacements;

            if (from.size() == to.size()) {
                replacements = List.of(to.get(j));
            } else {
                replacements = to;
            }

            for (String replacement : replacements) {
                // 빈 초성은 CONSONANT_DELETION에서 처리
                if (replacement.isEmpty()) {
                    continue;
                }

                int replacementIndex =
                        alphabet.indexOf(replacement);

                if (replacementIndex < 0) {
                    continue;
                }

                int initial = parts[0];
                int medial = parts[1];
                int fin = parts[2];

                if (component == 0) {
                    initial = replacementIndex;
                } else if (component == 1) {
                    medial = replacementIndex;
                } else {
                    fin = replacementIndex;
                }

                addVariant(
                        word,
                        index,
                        compose(initial, medial, fin),
                        variants
                );
            }
        }
    }

    // INITIAL: 첫 음절
    // MEDIAL: 첫 음절을 제외한 위치
    // INITIAL_OR_MEDIAL: 모든 음절의 초성
    private boolean matchesPosition(String position, int index) {
        return switch (position) {
            case "INITIAL" -> index == 0;
            case "MEDIAL" -> index > 0;
            case "INITIAL_OR_MEDIAL" -> true;
            default -> false;
        };
    }

    private boolean isHangul(char ch) {
        return ch >= HANGUL_START && ch <= HANGUL_END;
    }

    // 한글 완성형 음절을 초성, 중성, 종성 인덱스로 분해
    private int[] decompose(char syllable) {
        int offset = syllable - HANGUL_START;

        int initial = offset / (21 * 28);
        int medial = (offset % (21 * 28)) / 28;
        int fin = offset % 28;

        return new int[]{initial, medial, fin};
    }

    // 초성, 중성, 종성 인덱스를 완성형 한글로 조합
    private char compose(int initial, int medial, int fin) {
        return (char) (
                HANGUL_START
                        + initial * 21 * 28
                        + medial * 28
                        + fin
        );
    }

    private void addVariant(
            String word,
            int index,
            char replacement,
            Set<String> variants
    ) {
        String variant =
                word.substring(0, index)
                        + replacement
                        + word.substring(index + 1);

        variants.add(variant);
    }

    private List<String> readStrings(JsonNode array) {
        List<String> values = new ArrayList<>();

        if (array.isArray()) {
            for (JsonNode value : array) {
                values.add(value.asText());
            }
        }

        return values;
    }

    // 규칙별 분산 정렬
    private List<PronunciationVariant> diversifyVariants(
            List<PronunciationVariant> variants
    ) {
        Map<String, List<PronunciationVariant>> grouped =
                new LinkedHashMap<>();

        variants.stream()
                .sorted(
                        Comparator.comparingInt(
                                (PronunciationVariant variant) ->
                                        priorityRank(variant.priority())
                        ).reversed()
                )
                .forEach(variant ->
                        grouped.computeIfAbsent(
                                variant.ruleId(),
                                key -> new ArrayList<>()
                        ).add(variant)
                );

        List<PronunciationVariant> result =
                new ArrayList<>();

        int maxSize = grouped.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        for (int i = 0; i < maxSize; i++) {
            for (List<PronunciationVariant> group
                    : grouped.values()) {

                if (i < group.size()) {
                    result.add(group.get(i));
                }
            }
        }

        return result;
    }
}

