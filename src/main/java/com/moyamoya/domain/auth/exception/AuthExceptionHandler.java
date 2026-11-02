package com.moyamoya.domain.auth.exception;

import com.moyamoya.domain.auth.controller.AuthController;
import com.moyamoya.global.common.BaseResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = AuthController.class)
public class AuthExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> handleHttpMessageNotReadableException() {
        return ResponseEntity
                .status(AuthErrorCode.INVALID_SIGNUP_REQUEST.getHttpStatus())
                .body(BaseResponse.error(
                        AuthErrorCode.INVALID_SIGNUP_REQUEST.getCode(),
                        AuthErrorCode.INVALID_SIGNUP_REQUEST.getMessage()
                ));
    }
}
