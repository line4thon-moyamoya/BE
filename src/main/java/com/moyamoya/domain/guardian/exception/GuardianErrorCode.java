package com.moyamoya.domain.guardian.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GuardianErrorCode implements BaseErrorCode {

    INVALID_WORD_REQUEST(
            "GUARDIAN400_INVALID_WORD_REQUEST",
            "올바른 단어를 입력해 주세요.",
            HttpStatus.BAD_REQUEST
    ),

    ACCESS_FORBIDDEN(
            "GUARDIAN403",
            "해당 아이의 정보에 접근할 권한이 없습니다.",
            HttpStatus.FORBIDDEN
    ),

    WORD_REQUEST_NOT_FOUND(
            "GUARDIAN404_WORD_REQUEST",
            "물어볼 단어 요청을 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND
    ),

    WORD_REQUEST_ALREADY_RESOLVED(
            "GUARDIAN409_WORD_REQUEST_RESOLVED",
            "이미 해결된 단어 요청입니다.",
            HttpStatus.CONFLICT
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}

