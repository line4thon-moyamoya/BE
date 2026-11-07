package com.moyamoya.domain.guardian.controller;

import com.moyamoya.domain.guardian.dto.request.ResolveWordRequest;
import com.moyamoya.domain.guardian.dto.response.GuardianWordRequestResponse;
import com.moyamoya.domain.guardian.dto.response.ResolveWordResponse;
import com.moyamoya.domain.guardian.service.GuardianWordRequestService;
import com.moyamoya.global.common.BaseResponse;
import com.moyamoya.security.parentmode.ParentModeRequired;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Tag(
        name = "Guardian - Word Request",
        description = "보호자 모드 물어볼 단어 조회 및 해결 API"
)
@ParentModeRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/guardian/word-requests")
public class GuardianWordRequestController {

    private final GuardianWordRequestService guardianWordRequestService;

    private static final String LIST_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "물어볼 단어 요청 목록을 조회하였습니다.",
              "data": [
                {
                  "wordRequestId": 31,
                  "recognizedText": "저끔",
                  "correctedWord": null,
                  "audioUrl": "https://example.com/presigned-audio",
                  "status": "PENDING",
                  "createdAt": "2026-11-07T10:30:00",
                  "resolvedAt": null
                },
                {
                  "wordRequestId": 28,
                  "recognizedText": "데금",
                  "correctedWord": "대금",
                  "audioUrl": null,
                  "status": "RESOLVED",
                  "createdAt": "2026-11-05T15:20:00",
                  "resolvedAt": "2026-11-06T11:00:00"
                }
              ]
            }
            """;

    private static final String RESOLVE_REQUEST_EXAMPLE = """
            {
              "word": "적금"
            }
            """;

    private static final String RESOLVE_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "물어볼 단어 요청을 해결하였습니다.",
              "data": {
                "wordRequestId": 31,
                "recognizedText": "저끔",
                "correctedWord": "적금",
                "status": "RESOLVED",
                "resolvedAt": "2026-11-07T11:00:00"
              }
            }
            """;

    private static final String UNAUTHORIZED_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH401",
              "message": "인증이 필요합니다.",
              "data": null
            }
            """;

    private static final String FORBIDDEN_EXAMPLE = """
            {
              "success": false,
              "code": "GUARDIAN403",
              "message": "해당 아이의 정보에 접근할 권한이 없습니다.",
              "data": null
            }
            """;

    private static final String CHILD_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "code": "CHILD404",
              "message": "아이 정보를 찾을 수 없습니다.",
              "data": null
            }
            """;

    private static final String WORD_REQUEST_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "code": "GUARDIAN404_WORD_REQUEST",
              "message": "물어볼 단어 요청을 찾을 수 없습니다.",
              "data": null
            }
            """;

    private static final String INVALID_WORD_EXAMPLE = """
            {
              "success": false,
              "code": "GUARDIAN400_INVALID_WORD_REQUEST",
              "message": "올바른 단어를 입력해 주세요.",
              "data": null
            }
            """;

    private static final String ALREADY_RESOLVED_EXAMPLE = """
            {
              "success": false,
              "code": "GUARDIAN409_WORD_REQUEST_RESOLVED",
              "message": "이미 해결된 단어 요청입니다.",
              "data": null
            }
            """;

    @Operation(
            summary = "물어볼 단어 목록 조회",
            description = """
                    선택한 아이의 물어볼 단어 요청 목록을 조회합니다. 미해결(PENDING)과 해결(RESOLVED) 요청을 모두 반환합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @Parameter(
            name = "X-Parent-Mode-Token",
            in = ParameterIn.HEADER,
            required = true,
            description = "보호자 PIN 인증 후 발급받은 보호자 모드 토큰"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "물어볼 단어 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = LIST_SUCCESS_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 또는 보호자 모드 인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "아이 정보 접근 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "아이 정보를 찾을 수 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = CHILD_NOT_FOUND_EXAMPLE)
                    )
            )
    })
    @GetMapping
    public ResponseEntity<BaseResponse<List<GuardianWordRequestResponse>>>
    getWordRequests(
            Principal principal,
            @RequestParam Long childId
    ) {
        Long userId = Long.valueOf(principal.getName());

        List<GuardianWordRequestResponse> response =
                guardianWordRequestService.getWordRequests(
                        userId,
                        childId
                );

        return ResponseEntity.ok(
                BaseResponse.success(
                        "물어볼 단어 요청 목록을 조회하였습니다.",
                        response
                )
        );
    }

    @Operation(
            summary = "모야에게 알려주기 (물어볼 단어 해결)",
            description = """
                    보호자가 실제 단어를 입력하여 미해결 요청을 해결합니다. correctedWord를 저장하고 상태를 RESOLVED로 변경합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @Parameter(
            name = "X-Parent-Mode-Token",
            in = ParameterIn.HEADER,
            required = true,
            description = "보호자 PIN 인증 후 발급받은 보호자 모드 토큰"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "물어볼 단어 해결 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = RESOLVE_SUCCESS_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 단어 입력",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = INVALID_WORD_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 또는 보호자 모드 인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 요청에 접근할 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "단어 요청 또는 아이 정보 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "단어 요청 없음",
                                            value = WORD_REQUEST_NOT_FOUND_EXAMPLE
                                    ),
                                    @ExampleObject(
                                            name = "아이 정보 없음",
                                            value = CHILD_NOT_FOUND_EXAMPLE
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 해결된 요청",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = ALREADY_RESOLVED_EXAMPLE)
                    )
            )
    })
    @PatchMapping("/{wordRequestId}/resolve")
    public ResponseEntity<BaseResponse<ResolveWordResponse>>
    resolveWordRequest(
            Principal principal,
            @PathVariable Long wordRequestId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResolveWordRequest.class),
                            examples = @ExampleObject(value = RESOLVE_REQUEST_EXAMPLE)
                    )
            )
            @Valid @RequestBody ResolveWordRequest request
    ) {
        Long userId = Long.valueOf(principal.getName());

        ResolveWordResponse response =
                guardianWordRequestService.resolveWordRequest(
                        userId,
                        wordRequestId,
                        request.word()
                );

        return ResponseEntity.ok(
                BaseResponse.success(
                        "물어볼 단어 요청을 해결하였습니다.",
                        response
                )
        );
    }
}
