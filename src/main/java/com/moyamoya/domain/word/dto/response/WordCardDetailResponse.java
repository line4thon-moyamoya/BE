package com.moyamoya.domain.word.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "공통 단어 카드 상세 정보")
public record WordCardDetailResponse(
        @Schema(description = "단어 카드 ID", example = "35") Long learnedWordId,
        @Schema(description = "단어 ID", example = "1") Long wordId,
        @Schema(description = "단어명", example = "적금") String word,
        @Schema(description = "아이 연령별 설명. 미등록 시 null", example = "적금은 돈을 조금씩 모으는 거야.", nullable = true)
        String description,
        @Schema(description = "단어 이미지 URL. 미등록 시 null", example = "https://example.com/images/1.png", nullable = true)
        String imageUrl,
        @Schema(description = "저장된 연령별 설명 음성 값. 미등록 시 null. S3 재생 URL 변환은 추후 연동", example = "https://example.com/audio/1-desc.wav", nullable = true)
        String descriptionAudioUrl
) {
}
