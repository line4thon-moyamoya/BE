package com.moyamoya.domain.question.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class WordDescriptionService {

    private final OpenAiClient openAiClient;

    private String loadPrompt() {
        try {
            ClassPathResource resource =
                    new ClassPathResource(
                            "prompts/word-description.txt"
                    );

            try (var inputStream = resource.getInputStream()) {
                return new String(
                        inputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

        } catch (IOException e) {
            log.error("단어 설명 프롬프트 로드 실패", e);
            throw new IllegalStateException(
                    "단어 설명 프롬프트를 불러올 수 없습니다.", e
            );
        }
    }

    public String generate(
            String word,
            String definition,
            AgeGroup ageGroup
    ) {
        if (word == null || word.isBlank()
                || definition == null || definition.isBlank()) {
            return null;
        }

        String ageInstruction = ageGroup == AgeGroup.AGE_3_4
                ? "3~4세 설명 규칙"
                : "5세 이상 설명 규칙";

        String instructions = loadPrompt();

        String input = """
                다음 단어에 대한 쉬운 설명을 생성하세요.

                [단어]
                %s

                [사전 원본 정의]
                %s

                [적용할 연령별 규칙]
                %s

                해당 연령의 규칙만 적용하여
                쉬운 설명 한 개를 작성하세요.
                """.formatted(
                word,
                definition,
                ageInstruction
        );

        String result = openAiClient.generateText(
                instructions,
                input
        );

        if (result == null || result.isBlank()) {
            log.warn("쉬운 설명 생성 실패: word={}", word);
            return null;
        }

        return cleanDescription(result, word);
    }

    // LLM 응답에서 불필요한 단어/설명 라벨 제거
    private String cleanDescription(String result, String word) {
        if (result == null || result.isBlank()) {
            return null;
        }

        String cleaned = result.trim();

        // 실제 줄바꿈과 문자열 "\n" 모두 처리
        cleaned = cleaned.replace("\\n", "\n");

        // "단어: 컴퓨터" 형태의 첫 줄 제거
        cleaned = cleaned.replaceFirst(
                "^\\s*단어\\s*[:：]\\s*[^\\r\\n]*\\r?\\n\\s*",
                ""
        );

        // "설명:" 라벨 제거
        cleaned = cleaned.replaceFirst(
                "^\\s*설명\\s*[:：]\\s*",
                ""
        );

        return cleaned.trim();
    }
}
