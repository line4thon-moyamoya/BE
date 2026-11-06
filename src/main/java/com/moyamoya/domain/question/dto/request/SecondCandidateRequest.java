package com.moyamoya.domain.question.dto.request;

import com.moyamoya.domain.word.entity.HeardFrom;
import jakarta.validation.constraints.NotNull;

public record SecondCandidateRequest(
        @NotNull(message = "어디서 들었는지 선택해 주세요.")
        HeardFrom context
) {
}
