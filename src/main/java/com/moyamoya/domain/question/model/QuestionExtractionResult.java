package com.moyamoya.domain.question.model;

public record QuestionExtractionResult(
        boolean success,
        String originalSentence,
        String recognizedText,
        String failureReason
) {
    public static QuestionExtractionResult success(
            String originalSentence,
            String recognizedText // 조사를 포함한 추출 결과 저장
    ) {
        return new QuestionExtractionResult(
                true, originalSentence, recognizedText, null
        );
    }

    public static QuestionExtractionResult failure(
            String originalSentence,
            String reason
    ) {
        return new QuestionExtractionResult(
                false, originalSentence, null, reason
        );
    }
}