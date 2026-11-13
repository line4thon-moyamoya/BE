package com.moyamoya.domain.report.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.question.service.OpenAiClient;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.LearnedWord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiWeeklyReportConversationTopicService
        implements WeeklyReportConversationTopicService {

    private static final String PROMPT_PATH =
            "prompts/weekly-report.txt";

    private static final String FALLBACK_TOPIC =
            "오늘 배운 단어로 어떤 장면이 떠오르는지 물어봐 주세요.";

    private static final int MAX_TOPIC_LENGTH = 60;

    private static final String FORMAT_SUFFIX =
            "하고 물어봐 주세요.";

    private final OpenAiClient openAiClient;

    private String loadPrompt() {
        try {
            ClassPathResource resource =
                    new ClassPathResource(PROMPT_PATH);

            try (var inputStream = resource.getInputStream()) {
                return new String(
                        inputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

        } catch (IOException e) {
            log.error("주간 리포트 프롬프트 로드 실패", e);
            throw new IllegalStateException(
                    "주간 리포트 프롬프트를 불러올 수 없습니다.", e
            );
        }
    }

    @Override
    public String generate(
            Child child,
            List<LearnedWord> words,
            int unrecognizedCount
    ) {

        // 단어 기록이 없다면 LLM 호출 없이 기본 문구 반환
        if (child == null || words == null || words.isEmpty()) {
            return FALLBACK_TOPIC;
        }

        // 확정된 단어와 해결된 WordRequest의 수정 단어 사용
        String wordSummary = words.stream()
                .map(LearnedWord::word)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(10)
                .collect(Collectors.joining(", "));

        if (wordSummary.isBlank()) {
            return FALLBACK_TOPIC;
        }

        String ageGroup = mapAgeGroup(child.getAgeGroup());

        try {
            String instructions = loadPrompt();

            String input = """
                    아이가 이번 주에 질문한 단어 목록을 참고하여,
                    보호자가 아이에게 물어볼 대화 질문을 하나 생성하세요.

                    [아이 정보]
                    연령대: %s

                    [이번 주 질문 단어]
                    %s

                    [이번 주 통계]
                    못 찾은 말 수: %d

                    위 정보를 참고하여 지침에 맞는 질문 하나를 생성하세요.
                    """.formatted(
                    ageGroup,
                    wordSummary,
                    unrecognizedCount
            );

            String generated =
                    openAiClient.generateText(instructions, input);

            // 생성된 대화거리 검증 및 형식 보정
            String normalized = normalizeTopic(generated);

            if (normalized == null) {
                log.warn("주간 리포트 대화거리 형식 오류 - 기본 문구 사용");
                return FALLBACK_TOPIC;
            }

            return normalized;

        } catch (Exception e) {
            log.warn("주간 리포트 대화거리 생성 실패 - 기본 문구 사용", e);
            return FALLBACK_TOPIC;
        }
    }

    private String normalizeTopic(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw
                .replace("\r", " ")
                .replace("\n", " ")
                .trim()
                .replaceAll("\\s+", " ");

        // 질문을 감싸는 큰따옴표 제거
        normalized = normalized.replace("\"", "");

        // 질문은 반드시 하나만 허용
        long questionCount = normalized.chars()
                .filter(ch -> ch == '?')
                .count();

        if (questionCount != 1) {
            return null;
        }

        if (!normalized.endsWith(FORMAT_SUFFIX)) {
            String questionPart = normalized;

            if (questionPart.endsWith("요.")) {
                questionPart = questionPart.substring(
                        0,
                        questionPart.length() - 2
                ).trim();
            }

            if (!questionPart.endsWith("?")) {
                questionPart += "?";
            }

            normalized = questionPart + " " + FORMAT_SUFFIX;
        }

        if (normalized.length() > MAX_TOPIC_LENGTH) {
            return null;
        }

        if (!normalized.contains("?")) {
            return null;
        }

        return normalized;
    }

    // 아이의 연령대를 프롬프트용 문자열로 변환
    private String mapAgeGroup(AgeGroup ageGroup) {
        if (ageGroup == null) {
            return "만 5세 이상";
        }

        return switch (ageGroup) {
            case AGE_3_4 -> "만 3~4세";
            case AGE_5 -> "만 5세 이상";
        };
    }
}
