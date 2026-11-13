package com.moyamoya.domain.report.dto;

import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.LearnedWord;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.UnrecognizedWord;

import java.util.List;

public record WeeklyReportSnapshot(
        int questionCount,
        int unrecognizedCount,
        List<UnrecognizedWord> unrecognizedWords,
        List<LearnedWord> words,
        String conversationTopic
) {
}
