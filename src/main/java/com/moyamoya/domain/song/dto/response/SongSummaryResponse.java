package com.moyamoya.domain.song.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SongSummaryResponse(
        @Schema(description = "노래 ID", example = "1") Long songId,
        @Schema(description = "노래 제목", example = "우리 노래 1") String title,
        @Schema(description = "노래에 포함된 단어 5개. 카드 획득 순서") List<String> words
) {}
