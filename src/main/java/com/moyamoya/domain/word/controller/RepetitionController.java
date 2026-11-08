package com.moyamoya.domain.word.controller;

import com.moyamoya.domain.word.dto.response.RepetitionResponse;
import com.moyamoya.domain.word.service.RepetitionService;
import com.moyamoya.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/children/{childId}/words/{wordId}/repetitions")
@Tag(name = "Word", description = "단어 API")
public class RepetitionController {
    private final RepetitionService repetitionService;

    @Operation(summary = "따라 말하기 결과 및 카드 획득 처리",
            description = "음성을 STT로 변환하고 유사도 0.8 이상을 성공으로 판정합니다. "
                    + "등록된 허용 발음은 인정하며 다른 등록 단어와의 일치는 실패로 처리합니다. "
                    + "실패·무응답도 신규 카드를 저장하며, 성공 시 스티커를 반영합니다. "
                    + "카드 처리 후 결과·획득 안내 대사와 Base64 WAV 음성(audio/wav)을 반환합니다. "
                    + "TTS 실패 시 대사와 카드 처리 결과는 유지하고 음성 두 필드는 null로 반환합니다. "
                    + "신규 카드 획득 후 미사용 카드가 5장 이상이면 획득 순서대로 5장을 노래에 배정합니다. "
                    + "남은 couponCount와 이번에 등록한 songId(미등록 시 null)를 반환합니다. "
                    + "노래는 GENERATING으로 등록하며 음원 합성은 아직 연결하지 않습니다. "
                    + "STT 및 안내별 TTS 외부 호출 비용이 발생합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "판정 및 카드 처리 완료"),
            @ApiResponse(responseCode = "400", description = "ID 오류 또는 음성 파일 누락·형식·크기 오류 (최대 10MB)"),
            @ApiResponse(responseCode = "401", description = "로그인 필요"),
            @ApiResponse(responseCode = "403", description = "아이 접근 권한 없음"),
            @ApiResponse(responseCode = "404", description = "아이 또는 단어 없음"),
            @ApiResponse(responseCode = "502", description = "STT 서비스 오류, 카드 저장 안 함"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<RepetitionResponse>> repeat(
            Principal principal,
            @Parameter(description = "아이 ID", example = "1") @PathVariable("childId") Long childId,
            @Parameter(description = "단어 ID", example = "1") @PathVariable("wordId") Long wordId,
            @Parameter(description = "음성 파일 (wav/mp3/webm/m4a(audio/mp4))")
            @RequestPart("audio") MultipartFile audio
    ) {
        return ResponseEntity.ok(BaseResponse.success("따라 말하기 결과를 확인했습니다.",
                repetitionService.repeat(Long.valueOf(principal.getName()), childId, wordId, audio)));
    }
}
