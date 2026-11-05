package com.moyamoya.domain.consent.controller;

import com.moyamoya.domain.consent.dto.response.ConsentResponse;
import com.moyamoya.domain.consent.service.ConsentService;
import com.moyamoya.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Consent", description = "동의 항목 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/consents")
public class ConsentController {

    private static final String SUCCESS_MESSAGE = "동의 항목 목록을 조회했습니다.";
    private static final String SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "동의 항목 목록을 조회했습니다.",
              "data": [
                {
                  "consentId": 1,
                  "consentType": "TERMS_OF_SERVICE",
                  "content": "서비스 이용약관 내용"
                },
                {
                  "consentId": 2,
                  "consentType": "PRIVACY_POLICY",
                  "content": "개인정보 처리방침 내용"
                }
              ]
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
            summary = "동의 항목 목록 조회",
            description = "회원가입 화면에서 사용할 이용약관, 개인정보 처리방침, "
                    + "법정대리인 확인 및 음성 관련 동의 항목을 한 번에 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "동의 항목 목록 조회 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = SUCCESS_EXAMPLE)
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
    @GetMapping
    public ResponseEntity<BaseResponse<List<ConsentResponse>>> getConsents() {
        List<ConsentResponse> response = consentService.getConsents();
        return ResponseEntity.ok(BaseResponse.success(SUCCESS_MESSAGE, response));
    }
}
