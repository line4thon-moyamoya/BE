package com.moyamoya.domain.child.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChildErrorCode implements BaseErrorCode {
    INVALID_UPDATE_REQUEST("CHILD400", "수정할 아이 프로필 정보를 올바르게 입력해 주세요.", HttpStatus.BAD_REQUEST),
    EMPTY_UPDATE_REQUEST("CHILD400", "수정할 정보를 입력해 주세요.", HttpStatus.BAD_REQUEST),
    UPDATE_FORBIDDEN("CHILD403", "해당 아이 프로필을 수정할 권한이 없습니다.", HttpStatus.FORBIDDEN),
    CHILD_NOT_FOUND("CHILD404", "아이 프로필을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    INVALID_CREATE_REQUEST("CHILD400", "아이 프로필 정보를 올바르게 입력해 주세요.", HttpStatus.BAD_REQUEST),
    INVALID_NICKNAME("CHILD400_INVALID_NICKNAME", "아이 별명을 올바르게 입력해 주세요.", HttpStatus.BAD_REQUEST),
    INVALID_MOYA_TYPE("CHILD400_INVALID_MOYA", "올바른 모야 유형을 선택해 주세요.", HttpStatus.BAD_REQUEST),

    INVALID_AGE_GROUP(
            "CHILD400_INVALID_AGE",
            "올바른 나이 구간을 선택해 주세요.",
            HttpStatus.BAD_REQUEST
    ),

    HOME_ACCESS_FORBIDDEN(
        "CHILD403_HOME",
                "해당 아이의 홈 정보를 조회할 권한이 없습니다.",
        HttpStatus.FORBIDDEN
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
