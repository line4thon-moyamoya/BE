package com.moyamoya.domain.word.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WordErrorCode {

    WORD400(
            HttpStatus.BAD_REQUEST,
            "WORD400",
            "올바른 단어 정보를 입력해 주세요."
    ),

    WORD404(
            HttpStatus.NOT_FOUND,
            "WORD404",
            "단어를 찾을 수 없습니다."
    ),

    WORD_REQUEST400(
            HttpStatus.BAD_REQUEST,
            "WORD_REQUEST400",
            "올바른 단어를 입력해 주세요."
    ),

    WORD_REQUEST403(
            HttpStatus.FORBIDDEN,
            "WORD_REQUEST403",
            "해당 요청에 접근할 권한이 없습니다."
    ),

    WORD_REQUEST404(
            HttpStatus.NOT_FOUND,
            "WORD_REQUEST404",
            "물어볼 단어 요청을 찾을 수 없습니다."
    ),

    WORD_REQUEST409(
            HttpStatus.CONFLICT,
            "WORD_REQUEST409",
            "이미 해결된 요청입니다."
    ),

    LEARNED_WORD404(
            HttpStatus.NOT_FOUND,
            "LEARNED_WORD404",
            "학습한 단어를 찾을 수 없습니다."
    ),

    LEARNED_WORD409(
            HttpStatus.CONFLICT,
            "LEARNED_WORD409",
            "이미 학습한 단어입니다."
    ),

    QUESTION_HISTORY404(
            HttpStatus.NOT_FOUND,
            "QUESTION_HISTORY404",
            "질문 기록을 찾을 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
