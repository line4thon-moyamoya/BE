package com.moyamoya.domain.song.dto.response;

import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.word.entity.Word;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.util.StringUtils;

public record SongWordResponse(
        @Schema(description = "단어 ID", example = "1") Long wordId,
        @Schema(description = "단어명", example = "적금") String word,
        @Schema(description = "단어 이미지 URL. 미등록 시 null", nullable = true) String imageUrl,
        @Schema(description = "아이 연령별 단어 설명. 없으면 기본 정의 사용", nullable = true) String description
) {
    public static SongWordResponse from(Word word, AgeGroup ageGroup) {
        String description = switch (ageGroup) {
            case AGE_3_4 -> word.getDescAge3To4();
            case AGE_5 -> word.getDescAge5();
        };
        return new SongWordResponse(word.getId(), word.getWord(), word.getImageUrl(),
                StringUtils.hasText(description) ? description : word.getDefinition());
    }
}
