package com.moyamoya.domain.word.controller;

import com.moyamoya.domain.word.dto.response.WordCardDetailResponse;
import com.moyamoya.domain.word.service.WordCardService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/children/{childId}/learned-words")
@Tag(name = "Word", description = "단어 API")
public class WordCardController {
    private static final String SUCCESS_EXAMPLE = """
            {"success": true, "code": 200, "message": "단어 카드 상세 정보를 조회했습니다.",
             "data": {"learnedWordId": 35, "wordId": 1, "word": "적금", "description": "적금은 돈을 조금씩 모으는 거야.",
                      "imageUrl": "https://example.com/images/1.png", "descriptionAudioUrl": null}}
            """;
    private static final String INVALID_CARD_EXAMPLE = """
            {"success": false, "code": "LEARNED_WORD400", "message": "올바른 단어 카드 ID를 입력해 주세요.", "data": null}
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
    private static final String CARD_NOT_FOUND_EXAMPLE = """
            {"success": false, "code": "LEARNED_WORD404", "message": "단어 카드를 찾을 수 없습니다.", "data": null}
            """;
    private static final String WORD_NOT_FOUND_EXAMPLE = """
            {"success": false, "code": "WORD404", "message": "단어를 찾을 수 없습니다.", "data": null}
            """;
    private static final String SERVER_ERROR_EXAMPLE = """
            {"success": false, "code": "COMMON500", "message": "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", "data": null}
            """;

    private final WordCardService wordCardService;

    @Operation(summary = "공통 단어 카드 상세 조회",
            description = "획득한 카드의 연령별 설명, 이미지 및 저장된 설명 음성 값을 조회합니다. "
                    + "스티커와 NEW는 반환하거나 변경하지 않습니다. 미등록 값은 null입니다. "
                    + "S3 객체 키를 재생 가능한 Presigned URL로 변환하는 기능은 PR #18 반영 후 연동 예정입니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "카드 상세 조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE))),
            @ApiResponse(responseCode = "400", description = "아이 또는 카드 ID 형식 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = {@ExampleObject(name = "아이 ID 오류", value = INVALID_CHILD_EXAMPLE),
                                    @ExampleObject(name = "카드 ID 오류", value = INVALID_CARD_EXAMPLE)})),
            @ApiResponse(responseCode = "401", description = "로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "아이 소유권 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "아이, 카드 또는 단어 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = {@ExampleObject(name = "아이 없음", value = CHILD_NOT_FOUND_EXAMPLE),
                                    @ExampleObject(name = "카드 없음 또는 다른 아이의 카드", value = CARD_NOT_FOUND_EXAMPLE),
                                    @ExampleObject(name = "연결된 단어 없음", value = WORD_NOT_FOUND_EXAMPLE)})),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = SERVER_ERROR_EXAMPLE)))
    })
    @GetMapping("/{learnedWordId}")
    public ResponseEntity<BaseResponse<WordCardDetailResponse>> getWordCardDetail(
            Principal principal,
            @Parameter(description = "아이 ID", example = "1", required = true) @PathVariable("childId") Long childId,
            @Parameter(description = "획득한 단어 카드 ID", example = "1", required = true)
            @PathVariable("learnedWordId") Long learnedWordId
    ) {
        WordCardDetailResponse response = wordCardService.getWordCardDetail(Long.valueOf(principal.getName()), childId, learnedWordId);
        return ResponseEntity.ok(BaseResponse.success("단어 카드 상세 정보를 조회했습니다.", response));
    }
}
