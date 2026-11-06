package com.moyamoya.domain.word.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record RepetitionResponse(
        @Schema(description = "대상 단어 ID") Long wordId,
        @Schema(description = "획득한 단어 카드 ID") Long learnedWordId,
        @JsonProperty("isCorrect") @Schema(description = "따라 말하기 성공 여부") boolean isCorrect,
        @JsonProperty("isNewAcquisition") @Schema(description = "이번에 신규 카드를 획득했는지") boolean isNewAcquisition
) {}
