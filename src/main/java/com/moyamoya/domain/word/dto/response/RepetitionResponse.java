package com.moyamoya.domain.word.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moyamoya.domain.song.dto.response.SongRegistrationResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record RepetitionResponse(
        @Schema(description = "대상 단어 ID") Long wordId,
        @Schema(description = "획득한 단어 카드 ID") Long learnedWordId,
        @JsonProperty("isCorrect") @Schema(description = "따라 말하기 성공 여부") boolean isCorrect,
        @JsonProperty("isNewAcquisition") @Schema(description = "이번에 신규 카드를 획득했는지") boolean isNewAcquisition,
        @Schema(description = "노래 등록 후 남은 미사용 카드 수") long couponCount,
        @Schema(description = "이번 요청에서 등록한 노래 정보. 등록하지 않으면 null") SongRegistrationResponse song,
        @Schema(description = "따라 말하기 결과 안내") DialogueResponse repetitionDialogue,
        @Schema(description = "쿠폰 획득 또는 기존 카드 재학습 안내") DialogueResponse acquisitionDialogue
) {}
