// 자모 단위 발음 유사도 계산
package com.moyamoya.domain.question.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

@Component
public class PronunciationSimilarity {

    private static final int HANGUL_START = 0xAC00;
    private static final int HANGUL_END = 0xD7A3;

    private static final char[] INITIALS = {
            'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ',
            'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ',
            'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ'
    };

    private static final char[] MEDIALS = {
            'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ',
            'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ',
            'ㅜ', 'ㅝ', 'ㅞ', 'ㅟ', 'ㅠ', 'ㅡ',
            'ㅢ', 'ㅣ'
    };

    private static final char[] FINALS = {
            '\0', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ',
            'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ',
            'ㄾ', 'ㄿ', 'ㅀ', 'ㅁ', 'ㅂ', 'ㅄ',
            'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ',
            'ㅌ', 'ㅍ', 'ㅎ'
    };

    // 같은 위치의 자모를 비교하기 위한 내부 자료형
    private record Unit(char value, Type type) {}

    private enum Type {
        INITIAL, MEDIAL, FINAL, OTHER
    }

    // 두 표현의 발음 유사도: 0.0 ~ 1.0
    public double calculate(String spoken, String word) {
        if (spoken == null || word == null) {
            return 0.0;
        }

        String left = normalize(spoken);
        String right = normalize(word);

        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }

        if (left.equals(right)) {
            return 1.0;
        }

        List<Unit> a = decompose(left);
        List<Unit> b = decompose(right);

        double distance = editDistance(a, b);
        double maxCost = Math.max(totalWeight(a), totalWeight(b));

        return clamp(1.0 - distance / maxCost);
    }

    // 단어의 표기, 예상 발음, 실제 발음 중 가장 높은 점수
    public double calculateBest(
            String spoken,
            String word,
            String expectedPronunciation,
            String actualPronunciation
    ) {
        double best = calculate(spoken, word);

        for (String variant : List.of(
                safe(expectedPronunciation),
                safe(actualPronunciation)
        )) {
            if (!variant.isBlank()) {
                best = Math.max(best, calculate(spoken, variant));
            }
        }

        return best;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFC)
                .replaceAll("\\s+", "");
    }

    // 한글 음절을 초성, 중성, 종성으로 분해
    private List<Unit> decompose(String text) {
        List<Unit> result = new ArrayList<>();

        for (char ch : text.toCharArray()) {
            if (ch >= HANGUL_START && ch <= HANGUL_END) {
                int index = ch - HANGUL_START;

                int initial = index / (21 * 28);
                int medial = (index % (21 * 28)) / 28;
                int last = index % 28;

                result.add(new Unit(INITIALS[initial], Type.INITIAL));
                result.add(new Unit(MEDIALS[medial], Type.MEDIAL));

                if (last != 0) {
                    result.add(new Unit(FINALS[last], Type.FINAL));
                }
            } else {
                result.add(new Unit(ch, Type.OTHER));
            }
        }

        return result;
    }

    // 자모별 가중치
    private double weight(Unit unit) {
        return switch (unit.type()) {
            case INITIAL -> 1.0;
            case MEDIAL -> 1.2;
            case FINAL -> 0.8;
            case OTHER -> 1.0;
        };
    }

    // 자모 치환 비용
    private double substitutionCost(Unit a, Unit b) {
        if (a.equals(b)) {
            return 0.0;
        }

        // 같은 종류의 자모 변경
        if (a.type() == b.type()) {
            return Math.max(weight(a), weight(b));
        }

        // 서로 다른 위치의 자모 변경
        return weight(a) + weight(b);
    }

    // 가중 레벤슈타인 거리
    private double editDistance(List<Unit> a, List<Unit> b) {
        int n = a.size();
        int m = b.size();

        double[][] dp = new double[n + 1][m + 1];

        for (int i = 1; i <= n; i++) {
            dp[i][0] = dp[i - 1][0] + weight(a.get(i - 1));
        }

        for (int j = 1; j <= m; j++) {
            dp[0][j] = dp[0][j - 1] + weight(b.get(j - 1));
        }

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                Unit left = a.get(i - 1);
                Unit right = b.get(j - 1);

                double deletion = dp[i - 1][j] + weight(left);
                double insertion = dp[i][j - 1] + weight(right);
                double substitution =
                        dp[i - 1][j - 1] + substitutionCost(left, right);

                dp[i][j] = Math.min(
                        Math.min(deletion, insertion),
                        substitution
                );
            }
        }

        return dp[n][m];
    }

    private double totalWeight(List<Unit> units) {
        return units.stream()
                .mapToDouble(this::weight)
                .sum();
    }

    private double clamp(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }
}

