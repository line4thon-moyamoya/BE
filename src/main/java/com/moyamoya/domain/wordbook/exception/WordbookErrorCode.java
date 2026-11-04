package com.moyamoya.domain.wordbook.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WordbookErrorCode implements BaseErrorCode {
    CHILD_ACCESS_DENIED("CHILD403", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    CHILD_NOT_FOUND("CHILD404", "해당 아이를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
