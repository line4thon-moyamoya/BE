package com.moyamoya.domain.song.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record SongMixResponse(
        @Schema(description = "환경 프리픽스를 포함한 S3 음원 키") String audioKey,
        @Schema(description = "30분간 유효한 음원 재생 URL") String audioUrl
) {}
