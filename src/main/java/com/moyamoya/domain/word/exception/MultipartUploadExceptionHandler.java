package com.moyamoya.domain.word.exception;

import com.moyamoya.global.common.BaseResponse;
import com.moyamoya.global.exception.GlobalErrorCode;
import com.moyamoya.global.exception.model.BaseErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class MultipartUploadExceptionHandler {
    // Multipart 파싱은 컨트롤러 선택 전에 실패할 수 있어 범위 제한 없이 처리
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        boolean repetition = "POST".equals(request.getMethod())
                && path.matches("/api/v1/children/[^/]+/words/[^/]+/repetitions");
        BaseErrorCode error = repetition ? RepetitionErrorCode.INVALID_AUDIO : GlobalErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(error.getHttpStatus())
                .body(BaseResponse.error(error.getCode(), error.getMessage()));
    }
}
