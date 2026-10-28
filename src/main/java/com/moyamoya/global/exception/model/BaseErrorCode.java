package com.moyamoya.global.exception.model;

import org.springframework.http.HttpStatus;

public interface BaseErrorCode {

    String getCode();

    String getMessage();

    HttpStatus getHttpStatus();
}

