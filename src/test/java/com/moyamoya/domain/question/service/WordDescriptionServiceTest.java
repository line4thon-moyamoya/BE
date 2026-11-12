package com.moyamoya.domain.question.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WordDescriptionServiceTest {

    private OpenAiClient openAiClient;
    private WordDescriptionService service;

    @BeforeEach
    void setUp() {
        openAiClient = mock(OpenAiClient.class);
        service = new WordDescriptionService(openAiClient);
    }

    @Test
    void shouldRemoveWordAndDescriptionLabels() {
        when(openAiClient.generateText(anyString(), anyString()))
                .thenReturn("단어: 컴퓨터\n설명: 정보를 빠르게 처리하는 기계야.");

        String result = service.generate(
                "컴퓨터",
                "정보를 처리하는 전자 기계",
                AgeGroup.AGE_5
        );

        assertThat(result)
                .isEqualTo("정보를 빠르게 처리하는 기계야.");
    }

    @Test
    void shouldRemoveEscapedNewlineAndLabels() {
        when(openAiClient.generateText(anyString(), anyString()))
                .thenReturn("단어: 컴퓨터\\n설명: 정보를 빠르게 처리하는 기계야.");

        String result = service.generate(
                "컴퓨터",
                "정보를 처리하는 전자 기계",
                AgeGroup.AGE_5
        );

        assertThat(result)
                .isEqualTo("정보를 빠르게 처리하는 기계야.");
    }

    @Test
    void shouldPreserveNormalDescription() {
        when(openAiClient.generateText(anyString(), anyString()))
                .thenReturn("정보를 빠르게 처리하는 기계야.");

        String result = service.generate(
                "컴퓨터",
                "정보를 처리하는 전자 기계",
                AgeGroup.AGE_5
        );

        assertThat(result)
                .isEqualTo("정보를 빠르게 처리하는 기계야.");
    }

    @Test
    void shouldNotCallOpenAiWhenDefinitionIsBlank() {
        String result = service.generate(
                "컴퓨터",
                "",
                AgeGroup.AGE_5
        );

        assertThat(result).isNull();
        verifyNoInteractions(openAiClient);
    }
}
