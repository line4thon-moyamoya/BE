package com.moyamoya.domain.question.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExternalDictionaryService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public ExternalDictionaryService(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${dictionary.stdict.base-url}") String baseUrl,
            @Value("${dictionary.stdict.key}") String apiKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();

        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    public List<DictionaryEntry> search(String word) {

        if (word == null || word.isBlank()) {
            return List.of();
        }

        // 표제어 정확 일치 검색
        String response = restClient.get()
                .uri(builder -> builder
                        .path("/search.do")
                        .queryParam("key", apiKey)
                        .queryParam("q", word.trim())
                        .queryParam("req_type", "json")
                        .queryParam("advanced", "y")
                        .queryParam("target", 1)
                        .queryParam("method", "exact")
                        .build())
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);

            // API 오류를 '검색 결과 없음'과 구분
            if (root.has("error")) {
                throw new IllegalStateException(
                        "사전 API 오류: " + root.path("error").path("error_code").asText()
                );
            }

            JsonNode channel = root.path("channel");
            JsonNode items = channel.path("item");

            if (items.isMissingNode() || items.isNull()) {
                return List.of();
            }

            // API가 결과 1건을 객체로, 여러 건을 배열로 반환할 수 있음
            List<JsonNode> itemList = new ArrayList<>();

            if (items.isArray()) {
                items.forEach(itemList::add);
            } else if (items.isObject()) {
                itemList.add(items);
            }

            List<DictionaryEntry> results = new ArrayList<>();

            for (JsonNode item : itemList) {

                String foundWord = item.path("word").asText("");
                String entryId = item.path("target_code").asText("");

                // API 결과를 다시 한 번 정확 일치 검증
                if (!foundWord.equals(word.trim())) {
                    continue;
                }

                JsonNode sense = item.path("sense");

                if (sense.isArray()) {
                    for (JsonNode oneSense : sense) {
                        addEntry(results, foundWord, entryId, oneSense);
                    }
                } else {
                    addEntry(results, foundWord, entryId, sense);
                }
            }

            return results;

        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("사전 API 응답 파싱 실패", e);
        }
    }

    private void addEntry(
            List<DictionaryEntry> results,
            String word,
            String entryId,
            JsonNode sense
    ) {
        String definition = sense.path("definition").asText("");

        if (!definition.isBlank()) {
            results.add(new DictionaryEntry(
                    word,
                    definition,
                    "STDICT",
                    entryId
            ));
        }
    }
}
