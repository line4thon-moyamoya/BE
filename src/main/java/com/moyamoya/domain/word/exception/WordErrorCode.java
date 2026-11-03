package com.moyamoya.domain.word.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WordErrorCode implements BaseErrorCode {

    WORD400(
            "WORD400",
            "올바른 단어 정보를 입력해 주세요.",
            HttpStatus.BAD_REQUEST
    ),

    WORD404(
            "WORD404",
            "단어를 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND
    ),

    WORD_REQUEST400(
            "WORD_REQUEST400",
            "올바른 단어를 입력해 주세요.",
            HttpStatus.BAD_REQUEST
    ),

    WORD_REQUEST403(
            "WORD_REQUEST403",
            "해당 요청에 접근할 권한이 없습니다.",
            HttpStatus.FORBIDDEN
    ),

    WORD_REQUEST404(
            "WORD_REQUEST404",
            "물어볼 단어 요청을 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND
    ),

    WORD_REQUEST409(
            "WORD_REQUEST409",
            "이미 해결된 요청입니다.",
            HttpStatus.CONFLICT
    ),

    LEARNED_WORD404(
            "LEARNED_WORD404",
            "학습한 단어를 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND
    ),

    LEARNED_WORD409(
            "LEARNED_WORD409",
            "이미 학습한 단어입니다.",
            HttpStatus.CONFLICT
    ),

    QUESTION_HISTORY404(
            "QUESTION_HISTORY404",
            "질문 기록을 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
