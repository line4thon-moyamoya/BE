package com.moyamoya.domain.report.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WeeklyReportErrorCode implements BaseErrorCode {

    REPORT_NOT_FOUND(
            "REPORT404",
            "주간 리포트를 찾을 수 없습니다.",
            HttpStatus.NOT_FOUND
    ),

    REPORT_ALREADY_EXISTS(
            "REPORT409",
            "해당 주차의 리포트가 이미 존재합니다.",
            HttpStatus.CONFLICT
    ),

    REPORT_INVALID_WEEK(
            "REPORT400",
            "올바르지 않은 주차입니다.",
            HttpStatus.BAD_REQUEST
    ),

    REPORT_INVALID_RANGE(
            "REPORT400_RANGE",
            "올바르지 않은 조회 기간입니다.",
            HttpStatus.BAD_REQUEST
    ),

    REPORT_GENERATION_FAILED(
            "REPORT500",
            "주간 리포트 생성에 실패했습니다.",
            HttpStatus.INTERNAL_SERVER_ERROR
    );

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}