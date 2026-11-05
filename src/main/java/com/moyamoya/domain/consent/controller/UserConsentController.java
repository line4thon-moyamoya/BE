package com.moyamoya.domain.consent.controller;

import com.moyamoya.domain.consent.dto.request.ConsentStatusUpdateRequest;
import com.moyamoya.domain.consent.dto.response.ConsentStatusResponse;
import com.moyamoya.domain.consent.service.ConsentService;
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
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Consent", description = "동의 항목 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/consents")
public class UserConsentController {

    private static final String SUCCESS_MESSAGE = "음성 수집 동의 상태가 변경되었습니다.";
    private static final String SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "음성 수집 동의 상태가 변경되었습니다.",
              "data": {
                "consentType": "VOICE_PROCESSING",
                "isAgreed": false
              }
            }
            """;
    private static final String INVALID_REQUEST_EXAMPLE = """
            {
              "success": false,
              "code": "CONSENT400_INVALID_REQUEST",
              "message": "동의 여부를 입력해 주세요.",
              "data": null
            }
            """;
    private static final String UNAUTHORIZED_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH401",
              "message": "로그인이 필요한 요청입니다.",
              "data": null
            }
            """;
    private static final String CONSENT_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "code": "CONSENT404",
              "message": "등록되지 않은 동의 항목입니다.",
              "data": null
            }
            """;
    private static final String CONSENT_RECORD_NOT_FOUND_EXAMPLE = """
            {
              "success": false,
              "code": "CONSENT_RECORD404",
              "message": "사용자의 동의 내역을 찾을 수 없습니다.",
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

    private final ConsentService consentService;

    @Operation(
            summary = "음성 수집 동의 상태 변경",
            description = "현재 로그인 사용자의 음성 수집 동의를 철회하거나 다시 동의합니다. "
                    + "철회할 때는 false, 재동의할 때는 true를 전달하며 보호자 모드 인증이 필요합니다.",
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
                    description = "동의 상태 변경 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "동의 여부 누락",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = INVALID_REQUEST_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Access Token 누락 또는 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = UNAUTHORIZED_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "동의 항목 또는 사용자 동의 내역 없음",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "동의 항목 없음",
                                            value = CONSENT_NOT_FOUND_EXAMPLE
                                    ),
                                    @ExampleObject(
                                            name = "사용자 동의 내역 없음",
                                            value = CONSENT_RECORD_NOT_FOUND_EXAMPLE
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "서버 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = INTERNAL_SERVER_ERROR_EXAMPLE)
                    )
            )
    })
    @ParentModeRequired
    @PatchMapping("/VOICE_PROCESSING")
    public ResponseEntity<BaseResponse<ConsentStatusResponse>> updateVoiceProcessingConsent(
            Principal principal,
            @RequestBody(required = false) ConsentStatusUpdateRequest request
    ) {
        ConsentStatusResponse response = consentService.updateVoiceProcessingConsent(
                Long.valueOf(principal.getName()),
                request
        );
        return ResponseEntity.ok(BaseResponse.success(SUCCESS_MESSAGE, response));
    }
}
