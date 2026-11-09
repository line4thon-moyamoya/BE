package com.moyamoya.domain.song.controller;

import com.moyamoya.domain.song.dto.request.SongMixRequest;
import com.moyamoya.domain.song.service.SongMixingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("dev")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/test/songs")
@Tag(name = "Song Test", description = "개발용 노래 합성 테스트")
public class SongMixTestController {
    private final SongMixingService mixingService;

    @Operation(summary = "노래 합성 테스트",
            description = "단어 5개의 TTS를 반주와 합성해 WAV로 반환하고 로컬 출력 폴더에 저장합니다. 실제 TTS 호출 비용이 발생합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/mix", produces = "audio/wav")
    public ResponseEntity<byte[]> mix(@Valid @RequestBody SongMixRequest request) {
        var result = mixingService.mix(request.words());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.outputPath().getFileName().toString()).build().toString())
                .body(result.audio());
    }
}
