package com.moyamoya.domain.question.controller;

import com.moyamoya.domain.question.dto.request.ConfirmWordRequest;
import com.moyamoya.domain.question.dto.request.SecondCandidateRequest;
import com.moyamoya.domain.question.dto.response.ConfirmWordResponse;
import com.moyamoya.domain.question.dto.response.QuestionCandidateResponse;
import com.moyamoya.domain.question.dto.response.SecondCandidateResponse;
import com.moyamoya.domain.question.service.QuestionConfirmService;
import com.moyamoya.domain.question.service.QuestionService;
import com.moyamoya.domain.question.dto.response.WordRequestSaveResponse;
import com.moyamoya.domain.question.service.WordRequestSaveService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.security.Principal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Question", description = "아이 질문 및 단어 후보 탐색 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private static final String CLARIFY_REQUEST_EXAMPLE = """
            {
              "context": "PARENTS"
            }
            """;
    private static final String ANALYZE_SUCCESS_FOUND_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "1차 단어 후보를 조회했습니다.",
              "data": {
                "sessionId": "424f5cc7-5540-4036-b6dc-a2bde9218851",
                "status": "CANDIDATES_FOUND",
                "analysisType": "PARTICLE_REMOVAL",
                "recognizedText": "적금이 뭐야",
                "extractedWord": "적금",
                "candidateCount": 2,
                "candidates": [
                  {
                    "candidateId": "c_001",
                    "wordId": 157,
                    "word": "적금",
                    "description": "은행에 매달 정해진 돈을 넣어서 크게 모으는 거야.",
                    "imageUrl": null,
                    "wordAudioUrl": "https://example.com/presigned-word-audio",
                    "wordAudioBase64": null,
                    "wordAudioContentType": null
                  },
                  {
                    "candidateId": "c_002",
                    "wordId": 158,
                    "word": "예금",
                    "description": "돈을 은행에 맡겨 두었다가 나중에 찾는 거야.",
                    "imageUrl": null,
                    "wordAudioUrl": "https://example.com/presigned-word-audio",
                    "wordAudioBase64": null,
                    "wordAudioContentType": null
                  }
                ],
                "dialogue": {
                  "text": "적금? 예금? 어떤 거야?",
                  "audioUrl": null,
                  "audioBase64": "UklGRiQAAABXQVZF...",
                  "audioContentType": "audio/wav"
                },
                "nextStep": "CANDIDATE_SELECTION"
              }
            }
            """;
    private static final String ANALYZE_SUCCESS_EMPTY_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "1차 단어 후보를 찾지 못했습니다.",
              "data": {
                "sessionId": "89da3d80-0cff-4b4b-a96f-2cc5c1325ff5",
                "status": "NO_CANDIDATE",
                "analysisType": "NONE",
                "recognizedText": "엄마 데금이 뭐야",
                "extractedWord": "데금이",
                "candidateCount": 0,
                "candidates": [],
                "dialogue": null,
                "nextStep": "ASK_HEARD_FROM"
              }
            }
            """;
    private static final String ANALYZE_INVALID_REQUEST_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION400",
              "message": "질문 요청을 올바르게 입력해 주세요.",
              "data": null
            }
            """;
    private static final String ANALYZE_INVALID_AUDIO_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION400_INVALID_AUDIO",
              "message": "올바른 음성 파일을 업로드해 주세요.",
              "data": null
            }
            """;
    private static final String ANALYZE_UNAUTHORIZED_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH401",
              "message": "인증이 필요합니다.",
              "data": null
            }
            """;
    private static final String ANALYZE_FORBIDDEN_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION403",
              "message": "해당 질문 정보에 접근할 권한이 없습니다.",
              "data": null
            }
            """;
    private static final String ANALYZE_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "code": "CHILD404",
              "message": "아이 정보를 찾을 수 없습니다.",
              "data": null
            }
            """;
    private static final String ANALYZE_NO_SPEECH_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION422_NO_SPEECH",
              "message": "음성을 인식하지 못했어요. 다시 말해 주세요.",
              "data": null
            }
            """;
    private static final String ANALYZE_STT_FAILED_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION502_STT_FAILED",
              "message": "음성 인식 서비스에 문제가 발생했습니다.",
              "data": null
            }
            """;
    private static final String CLARIFY_SUCCESS_FOUND_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "2차 단어 후보를 조회했습니다.",
              "data": {
                "sessionId": "89da3d80-0cff-4b4b-a96f-2cc5c1325ff5",
                "status": "CANDIDATES_FOUND",
                "candidateCount": 1,
                "candidates": [
                  {
                    "candidateId": "c_001",
                    "wordId": 112,
                    "word": "연금",
                    "description": "나중을 위해 모아두는 돈이야.",
                    "imageUrl": "https://cdn.example.com/images/word-112.png",
                    "wordAudioUrl": "https://example.com/presigned-word-audio",
                    "wordAudioBase64": null,
                    "wordAudioContentType": null
                  }
                ],
                "dialogue": {
                  "text": "연금 말하는 거야?",
                  "audioUrl": null,
                  "audioBase64": "UklGRiQAAABXQVZF...",
                  "audioContentType": "audio/wav"
                },
                "nextStep": "FINAL_CANDIDATE_SELECTION"
              }
            }
            """;
    private static final String CLARIFY_SUCCESS_EMPTY_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "2차 단어 후보를 찾지 못했습니다.",
              "data": {
                "sessionId": "89da3d80-0cff-4b4b-a96f-2cc5c1325ff5",
                "status": "NO_CANDIDATE",
                "candidateCount": 0,
                "candidates": [],
                "dialogue": null,
                "nextStep": "WORD_REQUEST"
              }
            }
            """;
    private static final String CLARIFY_INVALID_CONTEXT_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION400_INVALID_CONTEXT",
              "message": "어디서 들었는지 올바르게 선택해 주세요.",
              "data": null
            }
            """;
    private static final String CLARIFY_INVALID_SESSION_ID_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION400_INVALID_SESSION",
              "message": "sessionId 형식이 올바르지 않습니다.",
              "data": null
            }
            """;
    private static final String CLARIFY_UNAUTHORIZED_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH401",
              "message": "인증이 필요합니다.",
              "data": null
            }
            """;
    private static final String CLARIFY_FORBIDDEN_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION403",
              "message": "해당 질문 정보에 접근할 권한이 없습니다.",
              "data": null
            }
            """;
    private static final String CLARIFY_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION404",
              "message": "존재하지 않거나 만료된 질문입니다.",
              "data": null
            }
            """;
    private static final String CLARIFY_CONFLICT_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION409",
              "message": "현재 질문 단계에서는 요청을 처리할 수 없습니다.",
              "data": null
            }
            """;
    private static final String CONFIRM_REQUEST_EXAMPLE = """
            {
              "candidateId": "c_001"
            }
            """;
    private static final String CONFIRM_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "단어를 확정했습니다.",
              "data": {
                "status": "CONFIRMED",
                "wordId": 159,
                "word": "국회의원",
                "description": "사람들이 뽑아서 나라의 법을 만들고 정하는 일을 하는 사람이야.",
                "imageUrl": "https://cdn.example.com/images/word-159.png",
                "wordAudioUrl": "https://example.com/presigned-word-audio",
                "nextStep": "WORD_LEARNING"
              }
            }
            """;
    private static final String CONFIRM_INVALID_REQUEST_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION400",
              "message": "질문 요청을 올바르게 입력해 주세요.",
              "data": null
            }
            """;
    private static final String CONFIRM_UNAUTHORIZED_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH401",
              "message": "인증이 필요합니다.",
              "data": null
            }
            """;
    private static final String CONFIRM_FORBIDDEN_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION403",
              "message": "해당 질문 정보에 접근할 권한이 없습니다.",
              "data": null
            }
            """;
    private static final String CONFIRM_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION404",
              "message": "존재하지 않거나 만료된 질문입니다.",
              "data": null
            }
            """;
    private static final String CONFIRM_CONFLICT_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION409",
              "message": "현재 질문 단계에서는 요청을 처리할 수 없습니다.",
              "data": null
            }
            """;
    private static final String WORD_REQUEST_SAVE_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "물어볼 단어로 저장되었습니다.",
              "data": {
                "wordRequestId": 15,
                "status": "PENDING",
                "nextStep": "WORD_REQUEST_SAVED"
              }
            }
            """;
    private static final String WORD_REQUEST_INVALID_SESSION_ID_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION400_INVALID_SESSION",
              "message": "sessionId 형식이 올바르지 않습니다.",
              "data": null
            }
            """;
    private static final String WORD_REQUEST_UNAUTHORIZED_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH401",
              "message": "인증이 필요합니다.",
              "data": null
            }
            """;
    private static final String WORD_REQUEST_FORBIDDEN_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION403",
              "message": "해당 질문 정보에 접근할 권한이 없습니다.",
              "data": null
            }
            """;
    private static final String WORD_REQUEST_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION404",
              "message": "존재하지 않거나 만료된 질문입니다.",
              "data": null
            }
            """;
    private static final String WORD_REQUEST_CONFLICT_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION409",
              "message": "현재 질문 단계에서는 요청을 처리할 수 없습니다.",
              "data": null
            }
            """;
    private static final String WORD_REQUEST_SAVE_FAILED_EXAMPLE = """
            {
              "success": false,
              "code": "QUESTION500_WORD_REQUEST_SAVE_FAILED",
              "message": "물어볼 단어를 저장하는 중 문제가 발생했습니다.",
              "data": null
            }
            """;

    private final QuestionService questionService;
    private final QuestionConfirmService questionConfirmService;
    private final WordRequestSaveService wordRequestSaveService;

    @Operation(
            summary = "음성 질문 분석 및 1차 후보 탐색",
            description = "음성 파일을 STT로 인식한 뒤 발음 유사도 기반으로 1차 후보를 최대 3개 탐색합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "음성 질문 분석 완료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(name = "후보 탐색 성공", value = ANALYZE_SUCCESS_FOUND_EXAMPLE),
                                    @ExampleObject(name = "후보 없음", value = ANALYZE_SUCCESS_EMPTY_EXAMPLE)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청값 오류 또는 잘못된 음성 파일",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(name = "잘못된 요청", value = ANALYZE_INVALID_REQUEST_EXAMPLE),
                                    @ExampleObject(name = "잘못된 음성 파일", value = ANALYZE_INVALID_AUDIO_EXAMPLE)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = ANALYZE_UNAUTHORIZED_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 아이의 질문을 생성할 권한이 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = ANALYZE_FORBIDDEN_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "아이 정보를 찾을 수 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = ANALYZE_NOT_FOUND_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "422",
                    description = "음성을 인식하지 못함",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = ANALYZE_NO_SPEECH_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "502",
                    description = "외부 STT 서비스 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = ANALYZE_STT_FAILED_EXAMPLE)
                    )
            )
    })
    @PostMapping(
            value = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<BaseResponse<QuestionCandidateResponse>> analyze(
            Principal principal,
            @RequestParam("childId") Long childId,
            @RequestPart("audio") MultipartFile audio
    ) {
        Long userId = Long.valueOf(principal.getName());
        QuestionCandidateResponse response =
                questionService.analyzeAudio(userId, childId, audio);

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
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(name = "후보 탐색 성공", value = CLARIFY_SUCCESS_FOUND_EXAMPLE),
                                    @ExampleObject(name = "후보 없음", value = CLARIFY_SUCCESS_EMPTY_EXAMPLE)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "context 누락 또는 잘못된 sessionId",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(name = "잘못된 context", value = CLARIFY_INVALID_CONTEXT_EXAMPLE),
                                    @ExampleObject(name = "잘못된 sessionId", value = CLARIFY_INVALID_SESSION_ID_EXAMPLE)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CLARIFY_UNAUTHORIZED_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 질문 세션에 접근할 권한이 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CLARIFY_FORBIDDEN_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "존재하지 않거나 만료된 질문 세션",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CLARIFY_NOT_FOUND_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "현재 단계에서 2차 후보 탐색 불가",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CLARIFY_CONFLICT_EXAMPLE)
                    )
            )
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
                            examples = @ExampleObject(value = CLARIFY_REQUEST_EXAMPLE)
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

    @Operation(
            summary = "단어 후보 확정",
            description = "1차 또는 2차 후보 중 선택한 단어를 확정하고 질문 기록을 저장합니다.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "단어 확정 완료",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CONFIRM_SUCCESS_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 요청 또는 후보 ID",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CONFIRM_INVALID_REQUEST_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "로그인이 필요합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CONFIRM_UNAUTHORIZED_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 질문 세션에 접근할 권한이 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CONFIRM_FORBIDDEN_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "질문 세션 또는 아이 정보 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CONFIRM_NOT_FOUND_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 확정했거나 확정할 수 없는 상태",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CONFIRM_CONFLICT_EXAMPLE)
                    )
            )
    })
    @PostMapping("/{sessionId}/confirm")
    public ResponseEntity<BaseResponse<ConfirmWordResponse>> confirm(
            Principal principal,
            @PathVariable String sessionId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConfirmWordRequest.class),
                            examples = @ExampleObject(value = CONFIRM_REQUEST_EXAMPLE)
                    )
            )
            @Valid @RequestBody ConfirmWordRequest request
    ) {
        Long userId = Long.valueOf(principal.getName());

        ConfirmWordResponse response = questionConfirmService.confirmWord(
                userId,
                sessionId,
                request.candidateId()
        );

        return ResponseEntity.ok(
                BaseResponse.success("단어를 확정했습니다.", response)
        );
    }

    @Operation(
            summary = "물어볼 단어 저장",
            description = """
                    2차 단어 후보를 모두 거절한 경우 호출합니다.
                    원본 음성을 S3에 업로드하고,
                    조사 제거 여부까지 판단한 표현을
                    물어볼 단어로 저장합니다.
                    """,
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "물어볼 단어 저장 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = WORD_REQUEST_SAVE_SUCCESS_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "잘못된 sessionId 또는 요청",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = WORD_REQUEST_INVALID_SESSION_ID_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증이 필요합니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = WORD_REQUEST_UNAUTHORIZED_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "해당 질문에 접근할 권한이 없습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = WORD_REQUEST_FORBIDDEN_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "질문 세션이 없거나 만료되었습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = WORD_REQUEST_NOT_FOUND_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "잘못된 질문 단계 또는 중복 저장",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = WORD_REQUEST_CONFLICT_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "물어볼 단어 저장 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = WORD_REQUEST_SAVE_FAILED_EXAMPLE)
                    )
            )
    })
    @PostMapping("/{sessionId}/word-request")
    public ResponseEntity<BaseResponse<WordRequestSaveResponse>>
    saveWordRequest(
            Principal principal,
            @PathVariable String sessionId
    ) {
        Long userId = Long.valueOf(principal.getName());

        WordRequestSaveResponse response =
                wordRequestSaveService.save(userId, sessionId);

        return ResponseEntity.ok(
                BaseResponse.success(
                        "물어볼 단어로 저장되었습니다.",
                        response
                )
        );
    }
}
