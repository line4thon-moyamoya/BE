package com.moyamoya.domain.report.dto.response;

import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.LearnedWord;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.UnrecognizedWord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record WeeklyReportPreviewResponse(
        Long childId,
        String childName,
        LocalDate startDate,
        LocalDate endDate,
        int questionCount,
        int unrecognizedCount,
        List<UnrecognizedWord> unrecognizedWords,
        List<LearnedWord> words,
        String conversationTopic,
        LocalDateTime snapshotAt
) {
}
