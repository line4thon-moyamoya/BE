package com.moyamoya.domain.word.model;

public record RepetitionEvaluation(Status status, double similarity) {
    public enum Status {
        SUCCESS,
        FAILURE,
        NO_SPEECH
    }

    public boolean isCorrect() {
        return status == Status.SUCCESS;
    }
}
