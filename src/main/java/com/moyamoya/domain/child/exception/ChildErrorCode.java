package com.moyamoya.domain.child.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChildErrorCode implements BaseErrorCode {

    INVALID_CREATE_REQUEST("CHILD400", "아이 프로필 정보를 올바르게 입력해 주세요.", HttpStatus.BAD_REQUEST),
    INVALID_NICKNAME("CHILD400_INVALID_NICKNAME", "아이 별명을 올바르게 입력해 주세요.", HttpStatus.BAD_REQUEST),
    INVALID_MOYA_TYPE("CHILD400_INVALID_MOYA", "올바른 모야 유형을 선택해 주세요.", HttpStatus.BAD_REQUEST),

    INVALID_AGE_GROUP(
            "CHILD400_INVALID_AGE",
            "올바른 나이 구간을 선택해 주세요.",
            HttpStatus.BAD_REQUEST
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
