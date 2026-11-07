package com.moyamoya.domain.guardian.controller;

import com.moyamoya.domain.guardian.dto.response.GuardianHomeResponse;
import com.moyamoya.domain.guardian.service.GuardianHomeService;
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

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/guardian/home")
@Tag(name = "Guardian - Home", description = "보호자 홈 조회 API")
public class GuardianHomeController {

    private final GuardianHomeService guardianHomeService;

    private static final String SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "보호자 홈 정보를 조회하였습니다.",
              "data": {
                "children": [
                  {"childId": 1, "name": "지우"},
                  {"childId": 2, "name": "서아"}
                ],
                "selectedChildId": 1,
                "childName": "지우",
                "weekStartDate": "2026-11-02",
                "weekEndDate": "2026-11-08",
                "questionCount": 12,
                "unrecognizedCount": 3,
                "pendingRequestCount": 5,
                "pendingRequestPreview": [
                  {"wordRequestId": 31, "recognizedText": "극정감자"},
                  {"wordRequestId": 28, "recognizedText": "저끔"}
                ]
              }
            }
            """;

    private static final String EMPTY_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "보호자 홈 정보를 조회하였습니다.",
              "data": {
                "children": [],
                "selectedChildId": null,
                "childName": null,
                "weekStartDate": null,
                "weekEndDate": null,
                "questionCount": 0,
                "unrecognizedCount": 0,
                "pendingRequestCount": 0,
                "pendingRequestPreview": []
              }
            }
            """;

    private static final String INVALID_CHILD_ID_EXAMPLE = """
            {
              "success": false,
              "code": "GLOBAL400",
              "message": "올바르지 않은 요청입니다.",
              "data": null
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

    private static final String PARENT_MODE_UNAUTHORIZED_EXAMPLE = """
            {
              "success": false,
              "code": "PARENT_MODE401",
              "message": "보호자 모드 인증이 필요합니다.",
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
              "message": "아이 프로필을 찾을 수 없습니다.",
              "data": null
            }
            """;

    private static final String INTERNAL_SERVER_ERROR_EXAMPLE = """
            {
              "success": false,
              "code": "COMMON500",
              "message": "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
              "data": null
            }
            """;

    @Operation(
            summary = "보호자 홈 조회",
            description = """
                    로그인한 보호자의 아이 목록과 선택된 아이의
                    이번 주 질문 활동 및 현재 미해결 요청을 조회합니다.

                    childId 생략 시 첫 번째 아이를 자동 선택합니다.
                    아이가 없으면 빈 응답을 반환합니다.

                    questionCount: 이번 주 질문 기록 + 물어볼 단어 전체
                    unrecognizedCount: 이번 주 생성된 미해결 요청
                    pendingRequestCount: 전체 기간 미해결 요청
                    pendingRequestPreview: 전체 기간 미해결 요청 최신 3개

                    이번 주는 한국 시간 월요일 00:00부터 시작합니다.
                    """,
            parameters = @Parameter(
                    name = "X-Parent-Mode-Token",
                    description = "보호자 PIN 인증 후 발급받은 Parent Mode Token",
                    required = true,
                    in = ParameterIn.HEADER
            ),
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "보호자 홈 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "아이 등록됨",
                                            value = SUCCESS_EXAMPLE
                                    ),
                                    @ExampleObject(
                                            name = "등록된 아이 없음",
                                            value = EMPTY_EXAMPLE
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "childId 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = INVALID_CHILD_ID_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인 또는 보호자 모드 인증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "로그인 인증 실패",
                                            value = UNAUTHORIZED_EXAMPLE
                                    ),
                                    @ExampleObject(
                                            name = "보호자 모드 인증 실패",
                                            value = PARENT_MODE_UNAUTHORIZED_EXAMPLE
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "다른 보호자의 아이 조회",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = FORBIDDEN_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "아이 정보 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = CHILD_NOT_FOUND_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = INTERNAL_SERVER_ERROR_EXAMPLE
                            )
                    )
            )
    })
    @ParentModeRequired
    @GetMapping
    public ResponseEntity<BaseResponse<GuardianHomeResponse>> getHome(
            Principal principal,
            @RequestParam(required = false) Long childId
    ) {
        Long userId = Long.valueOf(principal.getName());

        GuardianHomeResponse response =
                guardianHomeService.getHome(userId, childId);

        return ResponseEntity.ok(
                BaseResponse.success(
                        "보호자 홈 정보를 조회하였습니다.",
                        response
                )
        );
    }
}