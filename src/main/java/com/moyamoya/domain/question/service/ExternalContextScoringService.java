package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.model.WordCandidate;
import com.moyamoya.domain.word.entity.HeardFrom;
import com.moyamoya.domain.word.entity.WordSourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
public class ExternalContextScoringService {

    private static final int MAX_CONTEXT_CANDIDATES = 15;
    private static final int MAX_OUTPUT_TOKENS = 1024;

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final String prompt;

    public ExternalContextScoringService(
            OpenAiClient openAiClient,
            ObjectMapper objectMapper
    ) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.prompt = loadPrompt();
    }

    public Map<String, Double> scoreCandidates(
            String originalSentence,
            HeardFrom heardFrom,
            List<WordCandidate> candidates
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return Map.of();
        }

        // 발음 점수가 높은 후보부터 평가
        List<WordCandidate> evaluationCandidates =
                candidates.stream()
                        .filter(Objects::nonNull)
                        .filter(candidate ->
                                candidate.sourceType()
                                        == WordSourceType.EXTERNAL
                        )
                        .sorted(
                                Comparator.comparingDouble(
                                        WordCandidate::pronunciationScore
                                ).reversed()
                        )
                        .limit(MAX_CONTEXT_CANDIDATES)
                        .toList();

        if (evaluationCandidates.isEmpty()) {
            return Map.of();
        }

        try {
            String input = buildInput(
                    originalSentence,
                    heardFrom,
                    evaluationCandidates
            );

            String response = openAiClient.generateText(
                    prompt,
                    input,
                    MAX_OUTPUT_TOKENS
            );

            if (response == null || response.isBlank()) {
                return Map.of();
            }

            return parseScores(
                    response,
                    evaluationCandidates
            );

        } catch (IOException | RuntimeException e) {
            log.warn("외부 후보 문맥 평가 실패: {}", e.getMessage());
            return Map.of();
        }
    }

    // LLM에 전달할 입력 JSON 생성
    private String buildInput(
            String originalSentence,
            HeardFrom heardFrom,
            List<WordCandidate> candidates
    ) throws IOException {

        Map<String, Object> input = new LinkedHashMap<>();

        input.put(
                "originalSentence",
                originalSentence
        );

        input.put(
                "heardFrom",
                heardFrom == null
                        ? "UNKNOWN"
                        : heardFrom.name()
        );

        List<Map<String, Object>> candidateInputs =
                new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {

            WordCandidate candidate = candidates.get(i);

            Map<String, Object> item = new LinkedHashMap<>();

            item.put("index", i);
            item.put("word", candidate.word());
            item.put("definition", candidate.definition());

            candidateInputs.add(item);
        }

        input.put("candidates", candidateInputs);

        return objectMapper.writeValueAsString(input);
    }

    // LLM 응답 파싱 및 점수 검증
    private Map<String, Double> parseScores(
            String response,
            List<WordCandidate> candidates
    ) throws IOException {

        String cleaned = response.trim();

        // 코드 블록으로 반환한 경우 제거
        if (cleaned.startsWith("```")) {
            cleaned = cleaned
                    .replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }

        JsonNode root = objectMapper.readTree(cleaned);
        JsonNode scores = root.path("scores");

        if (!scores.isArray()) {
            return Map.of();
        }

        Map<String, Double> result = new LinkedHashMap<>();
        Set<Integer> seenIndices = new HashSet<>();

        for (JsonNode item : scores) {

            JsonNode indexNode = item.path("index");
            JsonNode scoreNode = item.path("score");

            if (!indexNode.isIntegralNumber()
                    || !indexNode.canConvertToInt()) {
                return Map.of();
            }

            int index = indexNode.asInt();

            // 존재하지 않는 후보 또는 중복 평가 방지
            if (index < 0
                    || index >= candidates.size()
                    || !seenIndices.add(index)) {
                return Map.of();
            }

            // 판단 근거가 부족한 경우 점수를 저장하지 않음
            if (scoreNode.isNull()) {
                continue;
            }

            if (!scoreNode.isNumber()) {
                return Map.of();
            }

            double score = scoreNode.asDouble();

            // 비정상적인 점수 제외
            if (!Double.isFinite(score)
                    || score < 0.0
                    || score > 1.0) {
                return Map.of();
            }

            String candidateId =
                    candidates.get(index).candidateId();

            result.put(candidateId, score);
        }

        // 일부 후보의 응답이 누락되면 전체 평가 실패 처리
        if (seenIndices.size() != candidates.size()) {
            return Map.of();
        }

        return Map.copyOf(result);
    }

    private String loadPrompt() {
        try {
            ClassPathResource resource =
                    new ClassPathResource(
                            "prompts/candidate-context.txt"
                    );

            try (var inputStream = resource.getInputStream()) {
                return new String(
                        inputStream.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "후보 문맥 평가 프롬프트 로딩 실패",
                    e
            );
        }
    }
}
