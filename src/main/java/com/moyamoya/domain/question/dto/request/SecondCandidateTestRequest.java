package com.moyamoya.domain.question.dto.request;

import com.moyamoya.domain.word.entity.HeardFrom;

import java.util.List;
import java.util.Set;

public record SecondCandidateTestRequest(
        List<String> targetExpressions,
        String originalText,
        HeardFrom heardFrom,
        Set<String> excludedCandidateKeys
) {
}
