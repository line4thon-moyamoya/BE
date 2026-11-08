package com.moyamoya.domain.song.dto.response;

import com.moyamoya.domain.song.entity.SongStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record SongRegistrationResponse(
        @Schema(description = "등록한 노래 ID") Long songId,
        @Schema(description = "노래 생성 상태", example = "GENERATING") SongStatus status
) {}
