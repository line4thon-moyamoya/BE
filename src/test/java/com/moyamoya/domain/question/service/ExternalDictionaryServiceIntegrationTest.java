package com.moyamoya.domain.question.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.model.DictionaryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalDictionaryServiceIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "STDICT_API_KEY", matches = ".+")
    void shouldReturnExactMatchesForKnownWord() {
        ExternalDictionaryService service = new ExternalDictionaryService(
                new ObjectMapper(),
                "https://stdict.korean.go.kr/api",
                System.getenv("STDICT_API_KEY")
        );

        List<DictionaryEntry> entries = service.searchExactMatches("사과");

        assertThat(entries).isNotEmpty();
        assertThat(entries)
                .allSatisfy(entry -> {
                    assertThat(entry.word()).isEqualTo("사과");
                    assertThat(entry.definition()).isNotBlank();
                });
    }
}
