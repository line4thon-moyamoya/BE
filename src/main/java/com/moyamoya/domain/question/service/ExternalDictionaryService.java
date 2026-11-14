package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.model.DictionaryEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        // 서버 연결 대기 최대 3초
        requestFactory.setConnectTimeout(3000);

        // 응답 데이터 대기 최대 5초
        requestFactory.setReadTimeout(5000);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
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
            return List.of();
        } catch (RuntimeException e) {
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    // 다른 뜻 조회 전용: 사전 호출 실패 시 예외 발생
    public List<DictionaryEntry> searchExactMatchesStrict(String word) {
        if (!StringUtils.hasText(word)) {
            throw new IllegalArgumentException("검색할 단어가 없습니다.");
        }

        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("사전 API 키가 설정되지 않았습니다.");
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
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new IllegalStateException("사전 응답이 비어 있습니다.");
            }

            JsonNode root = objectMapper.readTree(body);
            return parseEntries(root).stream()
                    .filter(entry -> normalized.equals(entry.word()))
                    .toList();

        } catch (Exception e) {
            throw new IllegalStateException("사전 조회에 실패했습니다.", e);
        }
    }

    // 다른 뜻 조회 전용
    public List<DictionaryEntry> searchAllMeaningsStrict(String word) {
        if (!StringUtils.hasText(word)) {
            throw new IllegalArgumentException("검색할 단어가 없습니다.");
        }

        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("사전 API 키가 없습니다.");
        }

        String normalized = word.trim();

        try {
            // 1. 표제어 검색
            URI searchUri = UriComponentsBuilder
                    .fromUriString(baseUrl + "/search.do")
                    .queryParam("key", apiKey)
                    .queryParam("q", "{searchWord}")
                    .queryParam("req_type", "json")
                    .queryParam("method", "exact")
                    .queryParam("num", 100)
                    .encode(StandardCharsets.UTF_8)
                    .buildAndExpand(normalized)
                    .toUri();

            String searchBody = restClient.get()
                    .uri(searchUri)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(searchBody)) {
                throw new IllegalStateException("사전 검색 응답이 비어 있습니다.");
            }

            JsonNode root = objectMapper.readTree(searchBody);
            JsonNode items = root.path("channel").path("item");

            List<DictionaryEntry> results = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();

            // 2. 검색된 항목별 상세 조회
            if (items.isArray()) {
                for (JsonNode item : items) {
                    try {
                        collectDetailMeanings(item, normalized, results, seen);
                    } catch (Exception e) {
                        // 개별 항목 실패는 다른 항목 처리 계속
                    }
                }
            } else if (items.isObject()) {
                try {
                    collectDetailMeanings(items, normalized, results, seen);
                } catch (Exception e) {
                    // 단일 항목 실패 시 빈 결과로 처리됨
                }
            }

            return results;

        } catch (Exception e) {
            throw new IllegalStateException("사전 상세 조회에 실패했습니다.", e);
        }
    }

    // target_code를 이용한 상세 뜻 수집
    private void collectDetailMeanings(
            JsonNode item,
            String expectedWord,
            List<DictionaryEntry> results,
            Set<String> seen
    ) {
        String word = clean(item.path("word").asText());
        String targetCode = item.path("target_code").asText();

        if (!expectedWord.equals(word)
                || !StringUtils.hasText(targetCode)) {
            return;
        }

        URI detailUri = UriComponentsBuilder
                .fromUriString(baseUrl + "/view.do")
                .queryParam("certkey_no", "11956")
                .queryParam("key", apiKey)
                .queryParam("type_search", "view")
                .queryParam("req_type", "json")
                .queryParam("method", "TARGET_CODE")
                .queryParam("q", targetCode)
                .build()
                .toUri();

        ResponseEntity<String> detailResponse = restClient.get()
                .uri(detailUri)
                .retrieve()
                .toEntity(String.class);

        String detailBody = detailResponse.getBody();

        if (!StringUtils.hasText(detailBody)) {
            throw new IllegalStateException(
                    "상세 조회 응답이 비어 있습니다: " + targetCode
            );
        }

        try {
            JsonNode detailRoot = objectMapper.readTree(detailBody);

            // 기존 walk()는 word/sense 구조를 탐색하므로 우선 재사용
            List<DictionaryEntry> detailEntries = parseDetailEntries(detailRoot);

            for (DictionaryEntry entry : detailEntries) {
                if (!expectedWord.equals(entry.word())) {
                    continue;
                }

                String key = entry.word() + "::" + entry.definition();

                if (seen.add(key)) {
                    results.add(entry);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "상세 응답 처리 실패: " + targetCode, e
            );
        }
    }

    // 상세 조회 응답에서 모든 뜻 추출
    private List<DictionaryEntry> parseDetailEntries(JsonNode root) {
        List<DictionaryEntry> entries = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        JsonNode items = root.path("channel").path("item");

        if (items.isArray()) {
            for (JsonNode item : items) {
                extractDetailMeanings(item, entries, seen);
            }
        } else if (items.isObject()) {
            extractDetailMeanings(items, entries, seen);
        }

        return entries;
    }

    // word_info -> pos_info -> comm_pattern_info -> sense_info 파싱
    private void extractDetailMeanings(
            JsonNode item,
            List<DictionaryEntry> entries,
            Set<String> seen
    ) {
        JsonNode wordInfo = item.path("word_info");
        String word = clean(wordInfo.path("word").asText());

        if (!StringUtils.hasText(word)) {
            return;
        }

        for (JsonNode posInfo : wordInfo.path("pos_info")) {
            String pos = clean(posInfo.path("pos").asText());

            for (JsonNode patternInfo : posInfo.path("comm_pattern_info")) {
                for (JsonNode senseInfo : patternInfo.path("sense_info")) {
                    String definition =
                            clean(senseInfo.path("definition").asText());

                    if (!StringUtils.hasText(definition)) {
                        continue;
                    }

                    String key = word + "::" + definition;

                    if (seen.add(key)) {
                        entries.add(
                                new DictionaryEntry(word, definition, pos)
                        );
                    }
                }
            }
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

        if (node.isArray()) {
            for (JsonNode child : node) {
                walk(child, entries, seen);
            }
            return;
        }

        if (!node.isObject()) {
            return;
        }

        String word = clean(node.path("word").asText());
        String pos = clean(node.path("pos").asText());
        JsonNode senses = node.path("sense");

        if (StringUtils.hasText(word)) {
            if (senses.isArray()) {
                for (JsonNode sense : senses) {
                    addEntry(word, pos, sense, entries, seen);
                }
            } else {
                addEntry(word, pos, senses, entries, seen);
            }
        }

        for (JsonNode child : node) {
            walk(child, entries, seen);
        }
    }

    private void addEntry(
            String word,
            String pos,
            JsonNode sense,
            List<DictionaryEntry> entries,
            Set<String> seen
    ) {
        String definition = clean(sense.path("definition").asText());

        if (StringUtils.hasText(definition)
                && seen.add(word + "::" + definition)) {
            entries.add(new DictionaryEntry(word, definition, pos));
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
