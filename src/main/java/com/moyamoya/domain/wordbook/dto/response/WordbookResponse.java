package com.moyamoya.domain.wordbook.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "단어 수첩 조회 응답")
public record WordbookResponse(
        @Schema(description = "아이가 획득한 전체 카드 수", example = "2") int totalCount,
        @Schema(description = "최신 획득순 카드 목록. 같은 획득 시각이면 카드 ID 내림차순", example = "[]")
        List<LearnedWordResponse> learnedWords
) {
}
