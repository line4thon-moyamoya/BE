// F2 활용 - 원문과 CSV의 힌트, 예문, 카테고리 관련성
package com.moyamoya.domain.question.service;

import com.moyamoya.domain.question.model.QuestionWordData;
import com.moyamoya.domain.word.entity.HeardFrom;
import com.moyamoya.domain.word.util.HeardFromMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ContextScoringService {

    private static final Pattern SPLIT_PATTERN =
            Pattern.compile("[^가-힣a-zA-Z0-9]+");

    // 질문에서 반복되는 일반적인 표현 제외
    private static final Set<String> STOP_WORDS = Set.of(
            "뭐야", "뭔데", "무슨", "알려줘",
            "설명해줘", "궁금해", "그거",
            "이거", "저거", "단어", "뜻"
    );

    // STT 원문과 단어 메타데이터의 문맥 유사도 계산
    public double calculateContextScore(
            String originalText,
            QuestionWordData data
    ) {
        Set<String> inputTokens = tokenize(originalText);

        if (inputTokens.isEmpty()) {
            return 0.0;
        }

        // 후보 단어 자체는 발음 점수에서 계산하므로 제외
        inputTokens.removeAll(tokenize(data.word()));
        inputTokens.removeAll(tokenize(data.matchKey()));

        if (inputTokens.isEmpty()) {
            return 0.0;
        }

        // 단어 설명과 원문 비교
        double hintScore =
                overlap(inputTokens, data.hint());

        // 실제 사용 예시와 비교
        double exampleScore = Math.max(
                overlap(inputTokens, data.example()),
                overlap(inputTokens, data.hearsExample())
        );

        // 카테고리와 비교
        double categoryScore =
                overlap(inputTokens, data.category());

        // 어디서 들었는지에 대한 상세 설명
        double heardNoteScore =
                overlap(inputTokens, data.heardNote());

        // 초기 실험용 가중치
        double score =
                hintScore * 0.35
                        + exampleScore * 0.35
                        + categoryScore * 0.20
                        + heardNoteScore * 0.10;

        return clamp(score);
    }

    // 아이가 선택한 출처와 CSV 단어 출처 비교
    public double calculateSourceScore(
            HeardFrom selectedHeardFrom,
            QuestionWordData data
    ) {
        if (selectedHeardFrom == null
                || selectedHeardFrom == HeardFrom.UNKNOWN) {
            return 0.0;
        }

        return HeardFromMapper.matches(
                selectedHeardFrom,
                data.heardFrom()
        ) ? 1.0 : 0.0;
    }

    // 입력 문장과 메타데이터 간 키워드 일치율
    private double overlap(
            Set<String> inputTokens,
            String reference
    ) {
        Set<String> referenceTokens = tokenize(reference);

        if (referenceTokens.isEmpty()) {
            return 0.0;
        }

        long matched = inputTokens.stream()
                .filter(input -> referenceTokens.stream()
                        .anyMatch(ref ->
                                input.equals(ref)
                                        || (
                                        input.length() >= 2
                                                && ref.length() >= 2
                                                && (
                                                input.contains(ref)
                                                        || ref.contains(input)
                                        )
                                )
                        ))
                .count();

        return clamp(
                (double) matched / inputTokens.size()
        );
    }

    // 문장을 단어 단위로 분리하고 조사 제거
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();

        if (text == null || text.isBlank()) {
            return tokens;
        }

        String normalized =
                text.toLowerCase(Locale.ROOT).trim();

        for (String token : SPLIT_PATTERN.split(normalized)) {
            String cleaned = removeParticle(token);

            if (cleaned.length() < 2
                    || STOP_WORDS.contains(cleaned)) {
                continue;
            }

            tokens.add(cleaned);
        }

        return tokens;
    }

    // 간단한 조사 제거
    private String removeParticle(String token) {
        String[] particles = {
                "에서", "에게", "한테", "으로",
                "하고", "이랑", "랑",
                "은", "는", "이", "가",
                "을", "를", "에", "도"
        };

        for (String particle : particles) {
            if (token.endsWith(particle)
                    && token.length() - particle.length() >= 2) {
                return token.substring(
                        0,
                        token.length() - particle.length()
                );
            }
        }

        return token;
    }

    private double clamp(double score) {
        return Math.max(0.0, Math.min(1.0, score));
    }
}

