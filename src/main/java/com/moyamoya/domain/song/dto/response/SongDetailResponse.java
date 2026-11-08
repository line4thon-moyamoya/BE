package com.moyamoya.domain.song.dto.response;

import com.moyamoya.domain.song.entity.SongStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SongDetailResponse(
        @Schema(description = "노래 ID", example = "12") Long songId,
        @Schema(description = "노래 제목", example = "우리 노래 1") String title,
        @Schema(description = "노래 생성 상태", example = "READY") SongStatus status,
        @Schema(description = "완성된 음원 재생 URL. 발급 후 30분 유효. 생성 중이거나 실패하면 null", nullable = true)
        String audioUrl,
        @Schema(description = "노래에 포함된 단어 5개. 카드 획득 순서. 생성 중이거나 실패하면 빈 목록")
        List<SongWordResponse> words
) {}
