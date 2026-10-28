package com.moyamoya.global.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GlobalErrorCode implements BaseErrorCode {

    INVALID_INPUT("GLOBAL400", "입력값을 확인해 주세요.", HttpStatus.BAD_REQUEST),
    MESSAGE_NOT_READABLE("GLOBAL400", "요청 본문을 읽을 수 없습니다.", HttpStatus.BAD_REQUEST),
    CONSTRAINT_VIOLATION("GLOBAL400", "요청 조건을 확인해 주세요.", HttpStatus.BAD_REQUEST),
    INTERNAL_SERVER_ERROR("GLOBAL500", "서버 내부 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}

