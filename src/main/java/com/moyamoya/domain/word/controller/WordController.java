package com.moyamoya.domain.word.controller;

import com.moyamoya.domain.word.dto.response.WordDescriptionResponse;
import com.moyamoya.domain.word.service.WordService;
import com.moyamoya.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/words")
@Tag(name = "Word", description = "단어 API")
public class WordController {
    private static final String SUCCESS_EXAMPLE = """
            {"success": true, "code": 200, "message": "단어 정보를 조회했습니다.",
             "data": {"wordId": 1, "word": "적금", "description": "적금은 돈을 조금씩 모으는 거야.",
                      "descriptionAudioUrl": "https://example.com/audio/1-desc.wav"}}
            """;
    private static final String INVALID_WORD_EXAMPLE = """
            {"success": false, "code": "WORD400", "message": "올바른 단어 ID를 입력해 주세요.", "data": null}
            """;
    private static final String INVALID_CHILD_EXAMPLE = """
            {"success": false, "code": "CHILD400", "message": "올바른 아이 ID를 입력해 주세요.", "data": null}
            """;
    private static final String UNAUTHORIZED_EXAMPLE = """
            {"success": false, "code": "AUTH401", "message": "로그인이 필요한 요청입니다.", "data": null}
            """;
    private static final String FORBIDDEN_EXAMPLE = """
            {"success": false, "code": "CHILD403", "message": "접근 권한이 없습니다.", "data": null}
            """;
    private static final String CHILD_NOT_FOUND_EXAMPLE = """
            {"success": false, "code": "CHILD404", "message": "해당 아이를 찾을 수 없습니다.", "data": null}
            """;
    private static final String WORD_NOT_FOUND_EXAMPLE = """
            {"success": false, "code": "WORD404", "message": "단어를 찾을 수 없습니다.", "data": null}
            """;
    private static final String SERVER_ERROR_EXAMPLE = """
            {"success": false, "code": "COMMON500", "message": "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", "data": null}
            """;

    private final WordService wordService;

    @Operation(summary = "아이 연령별 단어 설명 조회",
            description = "로그인한 보호자의 아이 연령에 맞는 설명을 조회하며, 없으면 기본 정의를 사용합니다. "
                    + "음성은 재사용하고 누락된 경우에만 TTS 생성·S3 저장합니다. 첫 생성에는 시간과 비용이 발생합니다. "
                    + "WAV 재생 URL은 발급 후 30분 유효하며, 설명과 기본 정의가 모두 없으면 음성은 null입니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "단어 설명 조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "ID 형식 오류 또는 childId 누락",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = {@ExampleObject(name = "단어 ID 오류", value = INVALID_WORD_EXAMPLE),
                                    @ExampleObject(name = "아이 ID 오류", value = INVALID_CHILD_EXAMPLE)})),
            @ApiResponse(responseCode = "401", description = "로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "아이 소유권 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "아이 또는 단어 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = {@ExampleObject(name = "아이 없음", value = CHILD_NOT_FOUND_EXAMPLE),
                                    @ExampleObject(name = "단어 없음", value = WORD_NOT_FOUND_EXAMPLE)})),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = SERVER_ERROR_EXAMPLE)))
    })
    @GetMapping("/{wordId}")
    public ResponseEntity<BaseResponse<WordDescriptionResponse>> getWordDescription(
            Principal principal,
            @Parameter(description = "단어 ID", example = "1", required = true) @PathVariable("wordId") Long wordId,
            @Parameter(description = "아이 ID. 연령대 및 소유권 확인용", example = "1", required = true)
            @RequestParam("childId") Long childId
    ) {
        WordDescriptionResponse response = wordService.getWordDescription(Long.valueOf(principal.getName()), wordId, childId);
        return ResponseEntity.ok(BaseResponse.success("단어 정보를 조회했습니다.", response));
    }
}
