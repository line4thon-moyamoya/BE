package com.moyamoya.domain.report.service;

import com.moyamoya.domain.child.entity.Child;
import com.moyamoya.domain.report.dto.response.WeeklyReportResponse.LearnedWord;

import java.util.List;

public interface WeeklyReportConversationTopicService {

    String generate(
            Child child,
            List<LearnedWord> words,
            int unrecognizedCount
    );
}
