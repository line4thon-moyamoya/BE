package com.moyamoya.domain.report.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WeeklyReportResponse(
        Long reportId,
        Long childId,
        String childName,
        LocalDate weekStartDate,
        LocalDate weekEndDate,
        int questionCount,
        int unrecognizedCount,
        List<UnrecognizedWord> unrecognizedWords,
        List<LearnedWord> words,
        String conversationTopic,
        LocalDateTime createdAt
) {

    public record UnrecognizedWord(
            String recognizedText
    ) {}

    public record LearnedWord(
            @JsonInclude(JsonInclude.Include.ALWAYS)
            Long wordId,
            String word,
            List<String> recognizedTexts,
            @JsonInclude(JsonInclude.Include.ALWAYS)
            String imageUrl
    ) {}
}
