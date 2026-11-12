package com.moyamoya.domain.report.service;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.question.service.OpenAiClient;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.LearnedWord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenAiWeeklyReportConversationTopicServiceTest {
    @Mock OpenAiClient openAiClient;
    @InjectMocks OpenAiWeeklyReportConversationTopicService service;

    @Test
    void generatesTopicWithExpectedPromptInput() {
        Child child = mock(Child.class);

        when(openAiClient.generateText(anyString(), anyString()))
                .thenReturn("적금으로 무엇을 하고 싶어?");

        String result = service.generate(
                child,
                List.of(
                        new LearnedWord(
                                10L,
                                "적금",
                                List.of("저금"),
                                null
                        )
                ),
                2
        );

        assertThat(result)
                .isEqualTo("적금으로 무엇을 하고 싶어? 하고 물어봐 주세요.");

        verify(openAiClient).generateText(
                argThat(instructions ->
                        instructions != null
                                && !instructions.isBlank()
                ),
                argThat(input ->
                        input.contains("연령대: 만 5세 이상")
                                && input.contains("적금")
                                && input.contains("못 찾은 말 수: 2")
                )
        );
    }

    @Test
    void emptyWords_doesNotCallOpenAi() {
        String result = service.generate(mock(Child.class), List.of(), 0);
        assertThat(result).contains("오늘 배운 단어");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void openAiFailure_returnsFallback() {
        when(openAiClient.generateText(anyString(), anyString())).thenThrow(new RuntimeException("timeout"));
        String result = service.generate(mock(Child.class),
                List.of(new LearnedWord(null, "극장", List.of("극정감자"), null)), 0);
        assertThat(result).contains("오늘 배운 단어");
    }

    @Test
    void openAiReturnsNull_returnsFallback() {
        when(openAiClient.generateText(anyString(), anyString()))
                .thenReturn(null);

        String result = service.generate(
                mock(Child.class),
                List.of(
                        new LearnedWord(
                                null,
                                "극장",
                                List.of("극정감자"),
                                null
                        )
                ),
                0
        );

        assertThat(result).contains("오늘 배운 단어");
    }

    @Test
    void generatedTopic_removesQuotationMarks() {
        Child child = mock(Child.class);

        when(openAiClient.generateText(anyString(), anyString()))
                .thenReturn("\"자외선 차단제를 발라본 적이 있어?\" 하고 물어봐 주세요.");

        String result = service.generate(
                child,
                List.of(new LearnedWord(
                        10L,
                        "자외선 차단제",
                        List.of("자외선 차단제"),
                        null
                )),
                0
        );

        assertThat(result)
                .isEqualTo("자외선 차단제를 발라본 적이 있어? 하고 물어봐 주세요.");
    }

    @Test
    void fallsBackWhenGeneratedTopicContainsTwoQuestions() {
        Child child = mock(Child.class);

        when(openAiClient.generateText(anyString(), anyString()))
                .thenReturn(
                        "적금으로 뭐 할까? 내일도 해볼까? 하고 물어봐 주세요."
                );

        List<WeeklyReportResponse.LearnedWord> words = List.of(
                new LearnedWord(
                        10L,
                        "적금",
                        List.of("저금"),
                        null
                )
        );

        String result = service.generate(child, words, 0);

        assertThat(result).isEqualTo(
                "오늘 배운 단어로 어떤 장면이 떠오르는지 물어봐 주세요."
        );

        // OpenAI 응답을 실제로 검증했는지 확인
        verify(openAiClient).generateText(anyString(), anyString());
    }
}
