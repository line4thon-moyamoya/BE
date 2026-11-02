package com.moyamoya.domain.consent.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ConsentErrorCode implements BaseErrorCode {

    REQUIRED_CONSENT_NOT_AGREED(
            "CONSENT400",
            "필수 약관에 모두 동의해 주세요.",
            HttpStatus.BAD_REQUEST
    ),
    CONSENT_NOT_FOUND(
            "CONSENT404",
            "등록되지 않은 동의 항목입니다.",
            HttpStatus.NOT_FOUND
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
