package com.moyamoya.global.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final BaseErrorCode errorCode;

    public CustomException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public CustomException(BaseErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

