package com.moyamoya.domain.word.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "아이 연령별 단어 설명")
public record WordDescriptionResponse(
        @Schema(description = "단어 ID", example = "1") Long wordId,
        @Schema(description = "단어명", example = "적금") String word,
        @Schema(description = "아이 연령대에 맞는 설명. 없으면 기본 정의 사용", example = "적금은 돈을 조금씩 모으는 거야.", nullable = true)
        String description,
        @Schema(description = "설명 WAV 재생 URL. 발급 후 30분 유효. 음성이 없으면 생성 후 반환하며 설명도 없으면 null", example = "https://example.com/audio/1-desc.wav", nullable = true)
        String descriptionAudioUrl
) {
}
