package com.moyamoya.domain.word.exception;

import com.moyamoya.domain.word.controller.WordController;
import com.moyamoya.global.common.BaseResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = WordController.class)
public class WordExceptionHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return error("childId".equals(exception.getName())
                ? WordErrorCode.INVALID_CHILD_ID : WordErrorCode.INVALID_WORD_ID);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<Void>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return error(WordErrorCode.INVALID_CHILD_ID);
    }

    private ResponseEntity<BaseResponse<Void>> error(WordErrorCode error) {
        return ResponseEntity.status(error.getHttpStatus())
                .body(BaseResponse.error(error.getCode(), error.getMessage()));
    }
}
