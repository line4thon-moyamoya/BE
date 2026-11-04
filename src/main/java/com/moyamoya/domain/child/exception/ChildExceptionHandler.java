package com.moyamoya.domain.child.exception;

import com.moyamoya.domain.child.controller.ChildController;
import com.moyamoya.global.common.BaseResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ChildController.class)
public class ChildExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception, HttpServletRequest request
    ) {
        ChildErrorCode error = "PATCH".equals(request.getMethod())
                ? ChildErrorCode.INVALID_UPDATE_REQUEST : ChildErrorCode.INVALID_CREATE_REQUEST;
        return ResponseEntity.status(error.getHttpStatus())
                .body(BaseResponse.error(error.getCode(), error.getMessage()));
    }
}
