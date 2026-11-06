package com.moyamoya.domain.word.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RepetitionErrorCode implements BaseErrorCode {
    INVALID_AUDIO("REPETITION400", "올바른 음성 파일을 전달해 주세요.", HttpStatus.BAD_REQUEST),
    STT_FAILED("REPETITION_STT_FAILED", "음성 인식 서비스에 문제가 발생했습니다. 다시 시도해 주세요.", HttpStatus.BAD_GATEWAY);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
