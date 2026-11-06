package com.moyamoya.domain.word.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이 연령별 단어 설명")
public record WordDescriptionResponse(
        @Schema(description = "단어 ID", example = "1") Long wordId,
        @Schema(description = "단어명", example = "적금") String word,
        @Schema(description = "아이 연령대에 맞는 설명. 미등록 시 null", example = "적금은 돈을 조금씩 모으는 거야.", nullable = true)
        String description,
        @Schema(description = "해당 연령별 설명 MP3 URL. 미등록 시 null", example = "https://example.com/audio/1-desc.mp3", nullable = true)
        String descriptionAudioUrl
) {
}
