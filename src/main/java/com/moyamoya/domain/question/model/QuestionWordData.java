package com.moyamoya.domain.question.model;

public record QuestionWordData(
        String word,
        String matchKey,
        String category,
        String heardFrom,
        String heardNote,
        String hearsExample,
        String pronPoint,
        String pronExpected,
        String pronActualAge3,
        String confusableWord,
        String definition,
        String hint,
        String descAge3To4,
        String descAge5,
        String example
) {
}
