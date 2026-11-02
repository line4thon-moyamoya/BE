package com.moyamoya.domain.auth.controller;

import com.moyamoya.domain.auth.dto.request.LoginRequest;
import com.moyamoya.domain.auth.dto.request.SignupRequest;
import com.moyamoya.domain.auth.dto.response.LoginResponse;
import com.moyamoya.domain.auth.dto.response.SignupResponse;
import com.moyamoya.domain.auth.service.AuthService;
import com.moyamoya.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String SIGNUP_SUCCESS_MESSAGE = "회원가입이 완료되었습니다.";
    private static final String LOGIN_SUCCESS_MESSAGE = "로그인에 성공했습니다.";
    private static final String SIGNUP_REQUEST_EXAMPLE = """
            {
              "email": "jiwoo.mom@email.com",
              "password": "password123!",
              "parentPin": "1234",
              "child": {
                "nickname": "지우",
                "ageGroup": "AGE_3_4"
              },
              "consents": [
                {
                  "consentId": 1,
                  "isAgreed": true
                },
                {
                  "consentId": 2,
                  "isAgreed": true
                },
                {
                  "consentId": 3,
                  "isAgreed": true
                },
                {
                  "consentId": 4,
                  "isAgreed": true
                }
              ]
            }
            """;
    private static final String SIGNUP_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "회원가입이 완료되었습니다.",
              "data": {
                "userId": 1,
                "childId": 1,
                "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
              }
            }
            """;
    private static final String INVALID_REQUEST_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH400",
              "message": "회원가입 요청값이 올바르지 않습니다.",
              "data": null
            }
            """;
    private static final String REQUIRED_CONSENT_EXAMPLE = """
            {
              "success": false,
              "code": "CONSENT400",
              "message": "필수 약관에 모두 동의해 주세요.",
              "data": null
            }
            """;
    private static final String INVALID_EMAIL_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH400_INVALID_EMAIL",
              "message": "올바른 이메일 형식으로 입력해 주세요.",
              "data": null
            }
            """;
    private static final String INVALID_PIN_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH400_INVALID_PIN",
              "message": "보호자 비밀번호는 숫자 4자리로 입력해 주세요.",
              "data": null
            }
            """;
    private static final String INVALID_AGE_EXAMPLE = """
            {
              "success": false,
              "code": "CHILD400_INVALID_AGE",
              "message": "올바른 나이 구간을 선택해 주세요.",
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
    private static final String DUPLICATE_EMAIL_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH409",
              "message": "이미 가입된 이메일입니다.",
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
    private static final String LOGIN_REQUEST_EXAMPLE = """
            {
              "email": "jiwoo.mom@email.com",
              "password": "password123!"
            }
            """;
    private static final String LOGIN_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": 200,
              "message": "로그인에 성공했습니다.",
              "data": {
                "userId": 1,
                "selectedChildId": 1,
                "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
                "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
              }
            }
            """;
    private static final String MISSING_LOGIN_CREDENTIALS_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH400",
              "message": "이메일과 비밀번호를 모두 입력해 주세요.",
              "data": null
            }
            """;
    private static final String INVALID_CREDENTIALS_EXAMPLE = """
            {
              "success": false,
              "code": "AUTH401",
              "message": "이메일 또는 비밀번호가 올바르지 않습니다.",
              "data": null
            }
            """;

    private final AuthService authService;

    @Operation(summary = "보호자 회원가입")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "회원가입 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = SIGNUP_SUCCESS_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청값 오류 또는 필수 약관 미동의",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(name = "잘못된 요청", value = INVALID_REQUEST_EXAMPLE),
                                    @ExampleObject(name = "필수 약관 미동의", value = REQUIRED_CONSENT_EXAMPLE),
                                    @ExampleObject(name = "잘못된 이메일", value = INVALID_EMAIL_EXAMPLE),
                                    @ExampleObject(name = "잘못된 PIN", value = INVALID_PIN_EXAMPLE),
                                    @ExampleObject(name = "잘못된 나이 구간", value = INVALID_AGE_EXAMPLE)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "등록되지 않은 동의 항목",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = CONSENT_NOT_FOUND_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "중복 이메일",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = DUPLICATE_EMAIL_EXAMPLE)
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
    @PostMapping("/signup")
    public ResponseEntity<BaseResponse<SignupResponse>> signup(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SignupRequest.class),
                            examples = @ExampleObject(value = SIGNUP_REQUEST_EXAMPLE)
                    )
            )
            @RequestBody SignupRequest request
    ) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.ok(BaseResponse.success(SIGNUP_SUCCESS_MESSAGE, response));
    }

    @Operation(summary = "보호자 로그인")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "로그인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = LOGIN_SUCCESS_EXAMPLE)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "이메일 또는 비밀번호 누락 및 이메일 형식 오류",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "이메일 또는 비밀번호 누락",
                                            value = MISSING_LOGIN_CREDENTIALS_EXAMPLE
                                    ),
                                    @ExampleObject(name = "잘못된 이메일", value = INVALID_EMAIL_EXAMPLE)
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "이메일 또는 비밀번호 불일치",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = BaseResponse.class),
                            examples = @ExampleObject(value = INVALID_CREDENTIALS_EXAMPLE)
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
    @PostMapping("/login")
    public ResponseEntity<BaseResponse<LoginResponse>> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(value = LOGIN_REQUEST_EXAMPLE)
                    )
            )
            @RequestBody LoginRequest request
    ) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(BaseResponse.success(LOGIN_SUCCESS_MESSAGE, response));
    }
}
