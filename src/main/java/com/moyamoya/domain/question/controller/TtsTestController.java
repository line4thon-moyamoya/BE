// TTS Leda 생성 -> 파이썬으로 튜닝 테스트용 api
package com.moyamoya.domain.question.controller;

import com.moyamoya.domain.question.service.MoyaTtsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/test/tts")
@Profile("dev")
public class TtsTestController {
    private final MoyaTtsService moyaTtsService;

    // Google TTS Leda 음성 생성 테스트용
    @GetMapping
    public ResponseEntity<byte[]> testTts(
            @RequestParam(defaultValue = "적금? 적응? 어떤 거야?") String text
    ) {
        byte[] audio = moyaTtsService.synthesize(text);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"tts-test.wav\""
                )
                .body(audio);
    }
}
