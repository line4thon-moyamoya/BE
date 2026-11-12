// 22개 발음 규칙 적용한 후보 생성기
package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PronunciationVariantGenerator {

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

    // F1 표현에서 신규 사전 검색용 후보 생성 - MVP: 기존 규칙을 1회만 적용하고 검색량을 제한
    public List<String> generateDictionaryQueries(String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }

        String original = expression.trim();
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(original);

        for (PronunciationVariant variant : generateDetailed(original)) {
            queries.add(variant.pronunciation());
            if (queries.size() >= 10) {
                break;
            }
        }

        return new ArrayList<>(queries);
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
}

