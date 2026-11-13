package com.moyamoya.domain.question.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.model.QuestionWordData;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Getter
@RequiredArgsConstructor
public class QuestionDataLoader {

    private final ObjectMapper objectMapper;

    private List<QuestionWordData> words = List.of();
    private Set<String> badwords = Set.of();

    private JsonNode config;
    private JsonNode pronunciationRules;

    @PostConstruct
    public void load() throws Exception {
        loadConfig();
        loadWords();
        loadBadwords();
        loadPronunciationRules();
    }

    private void loadConfig() throws Exception {
        ClassPathResource resource =
                new ClassPathResource("question/moya_config.json");

        try (var input = resource.getInputStream()) {
            config = objectMapper.readTree(input);
        }
    }

    private void loadWords() throws Exception {
        ClassPathResource resource =
                new ClassPathResource("question/moya_words_119.csv");

        List<QuestionWordData> loaded = new ArrayList<>();

        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                resource.getInputStream(),
                                StandardCharsets.UTF_8
                        )
                );
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreSurroundingSpaces(true)
                        .get()
                        .parse(reader)
        ) {
            for (CSVRecord record : parser) {
                loaded.add(new QuestionWordData(
                        value(record, "word"),
                        value(record, "match_key"),
                        value(record, "category"),
                        value(record, "heard_from"),
                        value(record, "heard_note"),
                        value(record, "hears_example"),
                        value(record, "pron_point"),
                        value(record, "pron_expected"),
                        value(record, "pron_actual_age3"),
                        value(record, "confusable_word"),
                        value(record, "definition"),
                        value(record, "hint"),
                        value(record, "desc_age_3_4"),
                        value(record, "desc_age_5"),
                        value(record, "example")
                ));
            }
        }

        words = List.copyOf(loaded);
    }

    private String value(CSVRecord record, String column) {
        String value = record.get(column);
        return value == null ? "" : value.trim();
    }

    private void loadBadwords() throws Exception {
        ClassPathResource resource =
                new ClassPathResource("question/moya_badwords_ko_564.txt");

        Set<String> loaded = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                )
        )) {
            String line;

            while ((line = reader.readLine()) != null) {
                String word = line.trim();

                if (!word.isBlank()) {
                    loaded.add(word);
                }
            }
        }

        badwords = Set.copyOf(loaded);
    }

    private void loadPronunciationRules() throws Exception {
        ClassPathResource resource =
                new ClassPathResource(
                        "question/moya_pronunciation_rules.json"
                );

        try (var input = resource.getInputStream()) {
            pronunciationRules = objectMapper.readTree(input);
        }
    }
}
