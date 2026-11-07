package com.moyamoya.domain.guardian.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveWordRequest(
        @NotBlank(message = "알려줄 단어를 입력해주세요.")
        @Size(max = 100, message = "단어는 100자 이하로 입력해주세요.")
        String word
) {
}
