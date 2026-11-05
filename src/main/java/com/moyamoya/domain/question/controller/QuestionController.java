package com.moyamoya.domain.question.controller;

import com.moyamoya.domain.question.dto.request.AnalyzeQuestionRequest;
import com.moyamoya.domain.question.dto.request.SecondCandidateRequest;
import com.moyamoya.domain.question.dto.response.QuestionCandidateResponse;
import com.moyamoya.domain.question.dto.response.SecondCandidateResponse;
import com.moyamoya.domain.question.service.QuestionService;
import com.moyamoya.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import java.security.Principal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Question", description = "아이 질문 및 단어 후보 탐색 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private final QuestionService questionService;

    @Operation(
            summary = "1차 단어 후보 탐색",
            description = "STT 문장을 분석하여 1차 후보를 최대 3개 탐색합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "1차 후보 탐색 완료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "요청값 오류"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "해당 아이의 질문을 생성할 권한이 없습니다."),
            @ApiResponse(responseCode = "404", description = "아이 정보를 찾을 수 없습니다.")
    })
    @PostMapping("/analyze")
    public ResponseEntity<BaseResponse<QuestionCandidateResponse>> analyze(
            Principal principal,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AnalyzeQuestionRequest.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "childId": 1,
                                              "recognizedSentence": "엄마 데금이 뭐야?"
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody AnalyzeQuestionRequest request
    ) {
        Long userId = Long.valueOf(principal.getName());
        QuestionCandidateResponse response = questionService.analyze(
                userId,
                request.childId(),
                request.recognizedSentence()
        );

        String message = response.candidateCount() > 0
                ? "1차 단어 후보를 조회했습니다."
                : "1차 단어 후보를 찾지 못했습니다.";

        return ResponseEntity.ok(BaseResponse.success(message, response));
    }

    @Operation(
            summary = "2차 단어 후보 탐색",
            description = "아이가 선택한 들은 곳(context)을 반영해 2차 후보를 최대 3개 탐색합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "2차 후보 탐색 완료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "context 누락 또는 잘못된 sessionId"),
            @ApiResponse(responseCode = "401", description = "로그인이 필요합니다."),
            @ApiResponse(responseCode = "403", description = "해당 질문 세션에 접근할 권한이 없습니다."),
            @ApiResponse(responseCode = "404", description = "존재하지 않거나 만료된 질문 세션"),
            @ApiResponse(responseCode = "409", description = "현재 단계에서 2차 후보 탐색 불가")
    })
    @PostMapping("/{sessionId}/clarify")
    public ResponseEntity<BaseResponse<SecondCandidateResponse>> clarify(
            Principal principal,
            @PathVariable String sessionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SecondCandidateRequest.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "context": "PARENTS"
                                            }
                                            """
                            )
                    )
            )
            @Valid @RequestBody SecondCandidateRequest request
    ) {
        Long userId = Long.valueOf(principal.getName());
        SecondCandidateResponse response = questionService.findSecondCandidates(
                userId,
                sessionId,
                request.context()
        );

        String message = response.candidateCount() > 0
                ? "2차 단어 후보를 조회했습니다."
                : "2차 단어 후보를 찾지 못했습니다.";

        return ResponseEntity.ok(BaseResponse.success(message, response));
    }
}
