package com.moyamoya.domain.song.exception;

import com.moyamoya.domain.song.controller.SongCouponController;
import com.moyamoya.domain.song.controller.SongController;
import com.moyamoya.global.common.BaseResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {SongCouponController.class, SongController.class})
public class SongExceptionHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        SongErrorCode error = "songId".equals(exception.getName())
                ? SongErrorCode.INVALID_SONG_ID : SongErrorCode.INVALID_CHILD_ID;
        return ResponseEntity.status(error.getHttpStatus())
                .body(BaseResponse.error(error.getCode(), error.getMessage()));
    }
}
