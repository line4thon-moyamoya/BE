package com.moyamoya.domain.word.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record DialogueResponse(
        @Schema(description = "안내 대사") String text,
        @Schema(description = "WAV 음성의 Base64 데이터. TTS 실패 시 null", nullable = true) String audioBase64,
        @Schema(description = "음성 형식. TTS 실패 시 null", example = "audio/wav", nullable = true) String audioContentType
) {}
