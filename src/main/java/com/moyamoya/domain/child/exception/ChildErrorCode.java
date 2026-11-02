package com.moyamoya.domain.child.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChildErrorCode implements BaseErrorCode {

    INVALID_AGE_GROUP(
            "CHILD400_INVALID_AGE",
            "올바른 나이 구간을 선택해 주세요.",
            HttpStatus.BAD_REQUEST
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
