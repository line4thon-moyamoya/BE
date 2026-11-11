package com.moyamoya.domain.song.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SongListResponse(
        @Schema(description = "최신 생성순 노래 목록. 같은 생성 시각이면 노래 ID 내림차순")
        List<SongSummaryResponse> songs
) {}
