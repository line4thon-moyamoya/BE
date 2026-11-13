package com.moyamoya.domain.report.controller;

import com.moyamoya.domain.report.dto.response.WeeklyReportResponse;
import com.moyamoya.domain.report.service.WeeklyReportService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;

@Tag(name = "Weekly Report", description = "보호자 모드 주간 리포트 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/guardian/weekly-reports")
public class WeeklyReportController {

    private final WeeklyReportService weeklyReportService;

    private static final String GET_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "주간 리포트를 조회하였습니다.",
              "data": {
                "reportId": 21,
                "childId": 1,
                "childName": "지우",
                "weekStartDate": "2026-11-09",
                "weekEndDate": "2026-11-15",
                "questionCount": 6,
                "unrecognizedCount": 1,
                "unrecognizedWords": [
                  {
                    "recognizedText": "극정감자"
                  }
                ],
                "words": [
                  {
                    "wordId": 10,
                    "word": "적금",
                    "recognizedTexts": ["저금", "저끔", "적금"],
                    "imageUrl": "https://example.com/presigned-image"
                  },
                  {
                    "wordId": 11,
                    "word": "영수증",
                    "recognizedTexts": ["폐니집"],
                    "imageUrl": "https://example.com/presigned-image"
                  },
                  {
                    "wordId": null,
                    "word": "통장",
                    "recognizedTexts": ["통장이"],
                    "imageUrl": null
                  }
                ],
                "conversationTopic": "영수증에는 어떤 정보가 적혀 있을까? 하고 물어봐 주세요.",
                "createdAt": "2026-11-16T00:10:00"
              }
            }
            """;

    private static final String INVALID_REQUEST_EXAMPLE = """
            {
              "success": false,
              "code": "GLOBAL400",
              "message": "입력값을 확인해 주세요.",
              "data": null
            }
            """;

    private static final String INVALID_WEEK_EXAMPLE = """
            {
              "success": false,
              "code": "REPORT400",
              "message": "올바르지 않은 주차입니다.",
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

    private static final String FORBIDDEN_EXAMPLE = """
            {
              "success": false,
              "code": "GUARDIAN403",
              "message": "해당 아이 정보에 접근할 권한이 없습니다.",
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

    private static final String REPORT_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "code": "REPORT404",
              "message": "해당 주간 리포트를 찾을 수 없습니다.",
              "data": null
            }
            """;

    private static final String GENERATION_FAILED_EXAMPLE = """
            {
              "success": false,
              "code": "REPORT500",
              "message": "주간 리포트 생성에 실패했습니다.",
              "data": null
            }
            """;

    @Operation(
            summary = "주간 리포트 조회",
            description = """
                    보호자 모드에서 특정 아이의 저장된 주간 리포트를 조회합니다.""",
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
                    description = "주간 리포트 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(
                                    name = "조회 성공",
                                    value = GET_SUCCESS_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 파라미터 오류 또는 월요일이 아닌 날짜",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "잘못된 요청",
                                            value = INVALID_REQUEST_EXAMPLE
                                    ),
                                    @ExampleObject(
                                            name = "잘못된 주 시작일",
                                            value = INVALID_WEEK_EXAMPLE
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증이 필요합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = UNAUTHORIZED_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "보호자 모드 인증 실패 또는 접근 권한 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = FORBIDDEN_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "아이 정보 또는 주간 리포트 없음",
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "아이 없음",
                                            value = CHILD_NOT_FOUND_EXAMPLE
                                    ),
                                    @ExampleObject(
                                            name = "리포트 없음",
                                            value = REPORT_NOT_FOUND_EXAMPLE
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "저장된 주간 리포트 처리 실패",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "리포트 처리 실패",
                                    value = GENERATION_FAILED_EXAMPLE
                            )
                    )
            )
    })
    @ParentModeRequired
    @GetMapping
    public ResponseEntity<BaseResponse<WeeklyReportResponse>> getWeeklyReport(
            Principal principal,

            @Parameter(
                    description = "조회할 아이 ID",
                    example = "1",
                    required = true
            )
            @RequestParam Long childId,

            @Parameter(
                    description = "조회할 주의 월요일 날짜",
                    example = "2026-11-09",
                    required = true
            )
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStartDate
    ) {
        Long userId = Long.valueOf(principal.getName());

        WeeklyReportResponse response =
                weeklyReportService.getReportForGuardian(
                        userId,
                        childId,
                        weekStartDate
                );

        return ResponseEntity.ok(
                BaseResponse.success(
                        "주간 리포트를 조회하였습니다.",
                        response
                )
        );
    }
}

