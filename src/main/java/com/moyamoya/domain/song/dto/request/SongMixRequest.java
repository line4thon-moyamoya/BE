package com.moyamoya.domain.song.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SongMixRequest(
        @NotNull @Size(min = 5, max = 5)
        @Schema(description = "합성할 단어 5개. 전달한 순서대로 배치", example = "[\"용기\",\"적금\",\"소방차\",\"알림장\",\"국회의원\"]")
        List<@NotBlank @Size(max = 100) String> words
) {}
