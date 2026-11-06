package com.moyamoya.domain.question.session;

public enum QuestionSessionStatus {
    PROCESSING,
    FIRST_CANDIDATES_READY,
    FIRST_NO_CANDIDATES,
    SECOND_CANDIDATES_READY,
    SECOND_NO_CANDIDATES,
    CONFIRMED,
    UNRESOLVED
}
