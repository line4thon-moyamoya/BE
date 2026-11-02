package com.moyamoya.domain.auth.exception;

import com.moyamoya.domain.auth.controller.AuthController;
import com.moyamoya.global.common.BaseResponse;
import com.moyamoya.global.exception.model.BaseErrorCode;
import jakarta.servlet.http.HttpServletRequest;
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
    public ResponseEntity<BaseResponse<Void>> handleHttpMessageNotReadableException(
            HttpServletRequest request
    ) {
        BaseErrorCode errorCode;
        if (request.getRequestURI().endsWith("/login")) {
            errorCode = AuthErrorCode.MISSING_LOGIN_CREDENTIALS;
        } else if (request.getRequestURI().endsWith("/parent-pin/verify")) {
            errorCode = ParentPinErrorCode.PARENT_PIN_REQUIRED;
        } else {
            errorCode = AuthErrorCode.INVALID_SIGNUP_REQUEST;
        }

        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(BaseResponse.error(
                        errorCode.getCode(),
                        errorCode.getMessage()
                ));
    }
}
