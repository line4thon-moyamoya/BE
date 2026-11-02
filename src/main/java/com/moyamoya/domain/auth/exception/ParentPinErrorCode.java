package com.moyamoya.domain.auth.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ParentPinErrorCode implements BaseErrorCode {

    PARENT_PIN_REQUIRED(
            "PARENT_PIN400",
            "보호자 비밀번호를 입력해 주세요.",
            HttpStatus.BAD_REQUEST
    ),
    INVALID_PARENT_PIN_FORMAT(
            "PARENT_PIN400_INVALID_FORMAT",
            "보호자 비밀번호는 숫자 4자리로 입력해 주세요.",
            HttpStatus.BAD_REQUEST
    ),
    PARENT_PIN_MISMATCH(
            "PARENT_PIN403",
            "보호자 비밀번호가 올바르지 않습니다.",
            HttpStatus.FORBIDDEN
    ),
    TOO_MANY_ATTEMPTS(
            "PARENT_PIN429",
            "잠시 후 다시 시도해 주세요.",
            HttpStatus.TOO_MANY_REQUESTS
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
