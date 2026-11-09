package com.moyamoya.domain.song.controller;

import com.moyamoya.domain.song.dto.request.SongMixRequest;
import com.moyamoya.domain.song.dto.response.SongMixResponse;
import com.moyamoya.domain.song.service.SongMixingService;
import com.moyamoya.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
    @Value("${aws.s3.prefix:}")
    private String s3Prefix;

    @Operation(summary = "노래 합성 테스트",
            description = "S3 반주와 단어 5개의 TTS를 합성해 S3에 저장하고 30분간 유효한 음원 URL을 반환합니다. local 프리픽스에서만 실행하며 실제 TTS 호출 비용이 발생합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping(value = "/mix", produces = "application/json")
    public ResponseEntity<BaseResponse<SongMixResponse>> mix(@Valid @RequestBody SongMixRequest request) {
        if (!"local".equals(s3Prefix)) {
            throw new IllegalStateException("노래 합성 테스트는 S3_PREFIX=local 설정에서만 실행할 수 있습니다.");
        }
        var result = mixingService.mix(request.words());
        return ResponseEntity.ok(BaseResponse.success("노래를 합성해 S3에 저장했습니다.", result));
    }
}
