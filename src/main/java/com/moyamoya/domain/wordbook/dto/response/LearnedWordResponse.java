package com.moyamoya.domain.wordbook.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moyamoya.domain.word.entity.LearnedWord;
import com.moyamoya.domain.word.entity.Word;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "단어 수첩 카드")
public record LearnedWordResponse(
        @Schema(description = "획득한 단어 카드 ID", example = "35") Long learnedWordId,
        @Schema(description = "단어 ID", example = "1") Long wordId,
        @Schema(description = "단어명", example = "적금") String word,
        @Schema(description = "이미지 URL. 이미지가 없으면 null", example = "/images/words/car.png", nullable = true)
        String imageUrl,
        @Schema(description = "칭찬 스티커 획득 여부", example = "true") boolean stickerEarned,
        @JsonProperty("isNew")
        @Schema(description = "최초 열람 전 true, 열람 후 false", example = "true") boolean isNew
) {
    public static LearnedWordResponse from(LearnedWord learnedWord) {
        Word word = learnedWord.getWord();
        return new LearnedWordResponse(learnedWord.getId(), word.getId(), word.getWord(),
                word.getImageUrl(), learnedWord.isStickerEarned(), learnedWord.isNew());
    }
}
