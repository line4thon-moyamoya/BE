package com.moyamoya.domain.song.exception;

import com.moyamoya.global.exception.model.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SongErrorCode implements BaseErrorCode {
    INVALID_CHILD_ID("CHILD400", "올바른 아이 ID를 입력해 주세요.", HttpStatus.BAD_REQUEST),
    CHILD_ACCESS_DENIED("CHILD403", "접근 권한이 없습니다.", HttpStatus.FORBIDDEN),
    CHILD_NOT_FOUND("CHILD404", "해당 아이를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    INVALID_SONG_ID("SONG400", "올바른 노래 ID를 입력해 주세요.", HttpStatus.BAD_REQUEST),
    SONG_NOT_FOUND("SONG404", "해당 노래를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;
}
