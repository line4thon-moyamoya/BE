package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
public class OpenAiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final boolean enabled;

    public OpenAiClient(
            ObjectMapper objectMapper,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-4.1-mini}") String model
    ) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.enabled = apiKey != null && !apiKey.isBlank();

        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + (enabled ? apiKey : ""))
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(3000);
                    setReadTimeout(12000);
                }})
                .build();
    }

    public String generateText(String instructions, String input) {
        if (!enabled) {
            log.warn("OpenAI API 키가 설정되지 않았습니다.");
            return null;
        }

        if (instructions == null || instructions.isBlank() || input == null || input.isBlank()) {
            return null;
        }

        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "instructions", instructions,
                    "input", input,
                    "max_output_tokens", 220,
                    "store", false
            );

            String response = restClient.post()
                    .uri("/responses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (response == null || response.isBlank()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response);
            for (JsonNode output : root.path("output")) {
                for (JsonNode content : output.path("content")) {
                    if ("output_text".equals(content.path("type").asText())) {
                        String text = content.path("text").asText(" ").trim();
                        if (!text.isBlank()) {
                            return text;
                        }
                    }
                }
            }

            log.warn("OpenAI 응답에서 텍스트를 찾지 못했습니다.");
            return null;
        } catch (Exception e) {
            log.warn("OpenAI API 호출 실패: {}", e.getMessage());
            return null;
        }
    }
}
