package com.moyamoya.domain.question.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyamoya.domain.question.config.QuestionDataLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JosaCandidateGenerator {

    private final QuestionDataLoader dataLoader;

    public List<String> generate(String recognizedText) {

        if (recognizedText == null || recognizedText.isBlank()) {
            return List.of();
        }

        String original = recognizedText.trim();

        // 원본 표현을 가장 먼저 보관하고 중복 제거
        Set<String> results = new LinkedHashSet<>();
        results.add(original);

        // 설정 파일에서 조사 목록 읽기
        JsonNode suffixes = dataLoader.getConfig()
                .path("f2_josa")
                .path("suffixes");

        List<JosaRule> rules = new ArrayList<>();

        for (JsonNode node : suffixes) {
            rules.add(new JosaRule(
                    node.path("josa").asText(),
                    node.path("needs_batchim").asBoolean()
            ));
        }

        // 긴 조사부터 검사 (이가 -> 이 등)
        rules.sort(
                Comparator.comparingInt(
                        (JosaRule rule) -> rule.josa().length()
                ).reversed()
        );

        for (JosaRule rule : rules) {

            String josa = rule.josa();

            if (!original.endsWith(josa)) {
                continue;
            }

            String stem = original.substring(
                    0,
                    original.length() - josa.length()
            ).trim();

            if (stem.isBlank()) {
                continue;
            }

            // 조사를 제거한 뒤 마지막 글자의 받침 검사
            boolean hasBatchim = hasBatchim(stem);

            if (hasBatchim == rule.needsBatchim()) {
                results.add(stem);
            }
        }

        return List.copyOf(results);
    }

    // 한글 완성형 유니코드에서 받침 유무 판별
    private boolean hasBatchim(String word) {
        char last = word.charAt(word.length() - 1);

        if (last < '가' || last > '힣') {
            return false;
        }

        return (last - '가') % 28 != 0;
    }

    private record JosaRule(
            String josa,
            boolean needsBatchim
    ) {
    }
}
