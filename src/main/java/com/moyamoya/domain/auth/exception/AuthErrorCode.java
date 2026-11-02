package com.moyamoya.domain.auth.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    INVALID_SIGNUP_REQUEST(
            "AUTH400",
            "회원가입 요청값이 올바르지 않습니다.",
            HttpStatus.BAD_REQUEST
    ),
    MISSING_LOGIN_CREDENTIALS(
            "AUTH400",
            "이메일과 비밀번호를 모두 입력해 주세요.",
            HttpStatus.BAD_REQUEST
    ),
    INVALID_EMAIL(
            "AUTH400_INVALID_EMAIL",
            "올바른 이메일 형식으로 입력해 주세요.",
            HttpStatus.BAD_REQUEST
    ),
    INVALID_PARENT_PIN(
            "AUTH400_INVALID_PIN",
            "보호자 비밀번호는 숫자 4자리로 입력해 주세요.",
            HttpStatus.BAD_REQUEST
    ),
    INVALID_CREDENTIALS(
            "AUTH401",
            "이메일 또는 비밀번호가 올바르지 않습니다.",
            HttpStatus.UNAUTHORIZED
    ),
    DUPLICATE_EMAIL(
            "AUTH409",
            "이미 가입된 이메일입니다.",
            HttpStatus.CONFLICT
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
