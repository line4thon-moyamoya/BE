package com.moyamoya.security.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SecurityErrorCode implements BaseErrorCode {

    AUTHENTICATION_REQUIRED("AUTH401", "로그인이 필요한 요청입니다.", HttpStatus.UNAUTHORIZED),
    INVALID_ACCESS_TOKEN("AUTH401", "유효하지 않은 Access Token입니다.", HttpStatus.UNAUTHORIZED),
    PARENT_MODE_TOKEN_REQUIRED(
            "PARENT_MODE401",
            "보호자 모드 인증이 필요한 요청입니다.",
            HttpStatus.UNAUTHORIZED
    ),
    INVALID_PARENT_MODE_TOKEN(
            "PARENT_MODE401",
            "유효하지 않은 보호자 모드 토큰입니다.",
            HttpStatus.UNAUTHORIZED
    ),
    PARENT_MODE_USER_MISMATCH(
            "PARENT_MODE403",
            "보호자 모드 인증 정보가 일치하지 않습니다.",
            HttpStatus.FORBIDDEN
    ),
    ACCESS_DENIED("AUTH403", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}

