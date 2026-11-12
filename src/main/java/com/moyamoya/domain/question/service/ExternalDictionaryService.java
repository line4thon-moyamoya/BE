package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.model.DictionaryEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class ExternalDictionaryService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;

    public ExternalDictionaryService(
            ObjectMapper objectMapper,
            @Value("${dictionary.stdict.base-url}") String baseUrl,
            @Value("${dictionary.stdict.key}") String apiKey
    ) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.restClient = RestClient.builder().build();
    }


    public List<DictionaryEntry> searchExactMatches(String word) {
        if (!StringUtils.hasText(word) || !StringUtils.hasText(apiKey)) {
            return List.of();
        }

        String normalized = word.trim();

        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl + "/search.do")
                .queryParam("key", apiKey)
                .queryParam("q", "{searchWord}")
                .queryParam("req_type", "json")
                .queryParam("method", "exact")
                .encode(StandardCharsets.UTF_8)
                .buildAndExpand(normalized)
                .toUri();

        try {
            String body = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new RestClientException("Dictionary API error: " + response.getStatusCode());
                    })
                    .body(String.class);

            if (body == null || body.isBlank()) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(body);
            return parseEntries(root);
        } catch (RestClientException e) {
            log.warn("사전 API 검색 실패: word={}", normalized, e);
            return List.of();
        } catch (RuntimeException e) {
            log.warn("사전 API 응답 파싱 실패: word={}", normalized, e);
            return List.of();
        } catch (Exception e) {
            log.warn("사전 API 응답 파싱 실패: word={}", normalized, e);
            return List.of();
        }
    }

    private List<DictionaryEntry> parseEntries(JsonNode root) {
        List<DictionaryEntry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        walk(root, entries, seen);
        return entries;
    }

    private void walk(
            JsonNode node,
            List<DictionaryEntry> entries,
            Set<String> seen
    ) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            String word = clean(node.path("word").asText());

            String definition = clean(
                    node.path("sense").path("definition").asText()
            );

            String pos = clean(node.path("pos").asText());

            if (StringUtils.hasText(word)
                    && StringUtils.hasText(definition)) {

                String key = word + "::" + definition;

                if (seen.add(key)) {
                    entries.add(
                            new DictionaryEntry(word, definition, pos)
                    );
                }
            }

            for (JsonNode child : node) {
                walk(child, entries, seen);
            }
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                walk(child, entries, seen);
            }
        }
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }

        return value
                .replace("^", " ")  // 사전 띄어쓰기 표시
                .replace("-", "")   // 사전 내부 하이픈 제거
                .replaceAll("\\s+", " ")
                .trim();
    }
}
