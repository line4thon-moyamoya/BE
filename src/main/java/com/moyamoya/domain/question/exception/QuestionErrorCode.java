package com.moyamoya.domain.question.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum QuestionErrorCode implements BaseErrorCode {

    INVALID_QUESTION_REQUEST(
            "QUESTION400",
            "질문 요청을 올바르게 입력해 주세요.",
            HttpStatus.BAD_REQUEST
    ),

    INVALID_SESSION_ID(
            "QUESTION400_INVALID_SESSION",
            "sessionId 형식이 올바르지 않습니다.",
            HttpStatus.BAD_REQUEST
    ),

    INVALID_CONTEXT(
            "QUESTION400_INVALID_CONTEXT",
            "어디서 들었는지 올바르게 선택해 주세요.",
            HttpStatus.BAD_REQUEST
    ),

    ACCESS_FORBIDDEN(
            "QUESTION403",
            "해당 질문 정보에 접근할 권한이 없습니다.",
            HttpStatus.FORBIDDEN
    ),

    SESSION_NOT_FOUND(
            "QUESTION404",
            "존재하지 않거나 만료된 질문입니다.",
            HttpStatus.NOT_FOUND
    ),

    INVALID_SESSION_STATE(
            "QUESTION409",
            "현재 질문 단계에서는 요청을 처리할 수 없습니다.",
            HttpStatus.CONFLICT
    ),

    INVALID_CANDIDATE(
            "QUESTION400_INVALID_CANDIDATE",
            "선택할 수 없는 단어 후보입니다.",
            HttpStatus.BAD_REQUEST
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
