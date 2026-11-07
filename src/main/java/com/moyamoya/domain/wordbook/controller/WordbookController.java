package com.moyamoya.domain.wordbook.controller;

import com.moyamoya.domain.wordbook.dto.response.WordbookResponse;
import com.moyamoya.domain.wordbook.service.WordbookService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/children/{childId}/learned-words")
@Tag(name = "Wordbook", description = "단어 수첩 API")
public class WordbookController {
    private static final String VIEWED_SUCCESS_EXAMPLE = """
            {"success": true, "code": 200, "message": "단어 카드 열람이 처리되었습니다.", "data": null}
            """;
    private static final String CARD_NOT_FOUND_EXAMPLE = """
            {"success": false, "code": "LEARNED_WORD404", "message": "단어 카드를 찾을 수 없습니다.", "data": null}
            """;
    private static final String SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "단어 수첩을 조회했습니다.",
              "data": {
                "totalCount": 2,
                "learnedWords": [
                  {"learnedWordId": 35, "wordId": 1, "word": "적금", "imageUrl": null, "stickerEarned": true, "isNew": true},
                  {"learnedWordId": 34, "wordId": 2, "word": "자동차", "imageUrl": "/images/words/car.png", "stickerEarned": false, "isNew": false}
                ]
              }
            }
            """;
    private static final String EMPTY_EXAMPLE = """
            {"success": true, "code": 200, "message": "단어 수첩을 조회했습니다.", "data": {"totalCount": 0, "learnedWords": []}}
            """;
    private static final String UNAUTHORIZED_EXAMPLE = """
            {"success": false, "code": "AUTH401", "message": "로그인이 필요한 요청입니다.", "data": null}
            """;
    private static final String FORBIDDEN_EXAMPLE = """
            {"success": false, "code": "CHILD403", "message": "접근 권한이 없습니다.", "data": null}
            """;
    private static final String NOT_FOUND_EXAMPLE = """
            {"success": false, "code": "CHILD404", "message": "해당 아이를 찾을 수 없습니다.", "data": null}
            """;
    private static final String SERVER_ERROR_EXAMPLE = """
            {"success": false, "code": "COMMON500", "message": "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", "data": null}
            """;

    private final WordbookService wordbookService;

    @Operation(summary = "단어 카드 열람 처리",
            description = "로그인한 보호자 소유 아이의 카드 NEW 배지를 해제합니다. "
                    + "이미 열람한 카드도 200을 반환하며 요청 본문은 필요하지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "열람 처리 성공. 이미 열람한 카드도 정상 처리",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = VIEWED_SUCCESS_EXAMPLE))),
            @ApiResponse(responseCode = "401", description = "로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "다른 보호자의 아이에 대한 접근",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "아이 또는 해당 아이의 카드 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(name = "아이 없음", value = NOT_FOUND_EXAMPLE),
                                    @ExampleObject(name = "카드 없음 또는 다른 아이의 카드", value = CARD_NOT_FOUND_EXAMPLE)
                            })),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = SERVER_ERROR_EXAMPLE)))
    })
    @PatchMapping("/{learnedWordId}/viewed")
    public ResponseEntity<BaseResponse<Void>> markAsViewed(
            Principal principal,
            @Parameter(description = "아이 ID", example = "1", required = true)
            @PathVariable("childId") Long childId,
            @Parameter(description = "열람한 단어 카드 ID", example = "1", required = true)
            @PathVariable("learnedWordId") Long learnedWordId
    ) {
        wordbookService.markAsViewed(Long.valueOf(principal.getName()), childId, learnedWordId);
        return ResponseEntity.ok(BaseResponse.success("단어 카드 열람이 처리되었습니다.", null));
    }

    @Operation(summary = "단어 수첩 조회",
            description = "로그인한 보호자의 아이가 획득한 카드를 최신 획득순으로 조회합니다. "
                    + "카드가 없으면 빈 목록을 반환하며, 조회만으로 NEW 배지를 해제하지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "단어 수첩 조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(name = "카드 목록", value = SUCCESS_EXAMPLE),
                                    @ExampleObject(name = "빈 수첩", value = EMPTY_EXAMPLE)
                            })),
            @ApiResponse(responseCode = "401", description = "로그인 필요",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "다른 보호자의 아이에 대한 접근",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "아이 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = SERVER_ERROR_EXAMPLE)))
    })
    @GetMapping
    public ResponseEntity<BaseResponse<WordbookResponse>> getWordbook(
            Principal principal,
            @Parameter(description = "단어 수첩을 조회할 아이 ID", example = "1", required = true)
            @PathVariable("childId") Long childId
    ) {
        WordbookResponse response = wordbookService.getWordbook(Long.valueOf(principal.getName()), childId);
        return ResponseEntity.ok(BaseResponse.success("단어 수첩을 조회했습니다.", response));
    }
}
