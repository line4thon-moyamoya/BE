package com.moyamoya.domain.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.auth.dto.request.ChildSignupRequest;
import com.moyamoya.domain.auth.dto.request.ConsentAgreementRequest;
import com.moyamoya.domain.auth.dto.request.LoginRequest;
import com.moyamoya.domain.auth.dto.request.ParentPinVerifyRequest;
import com.moyamoya.domain.auth.dto.request.SignupRequest;
import com.moyamoya.domain.auth.dto.request.TokenRefreshRequest;
import com.moyamoya.domain.auth.dto.response.LoginResponse;
import com.moyamoya.domain.auth.dto.response.LogoutResponse;
import com.moyamoya.domain.auth.dto.response.ParentPinVerifyResponse;
import com.moyamoya.domain.auth.dto.response.SignupResponse;
import com.moyamoya.domain.auth.dto.response.TokenRefreshResponse;
import com.moyamoya.domain.auth.exception.AuthErrorCode;
import com.moyamoya.domain.auth.exception.AuthExceptionHandler;
import com.moyamoya.domain.auth.exception.ParentPinErrorCode;
import com.moyamoya.domain.auth.service.AuthService;
import com.moyamoya.domain.consent.exception.ConsentErrorCode;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new AuthExceptionHandler(), new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void signupReturnsExpectedResponse() throws Exception {
        SignupResponse response = new SignupResponse(1L, 2L, "access-token", "refresh-token");
        when(authService.signup(any(SignupRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("회원가입이 완료되었습니다."))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.childId").value(2))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    void signupReturnsDuplicateEmailError() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new CustomException(AuthErrorCode.DUPLICATE_EMAIL));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH409"))
                .andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void signupReturnsAuth400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH400"))
                .andExpect(jsonPath("$.message").value("회원가입 요청값이 올바르지 않습니다."));
    }

    @Test
    void signupReturnsConsent400WhenRequiredConsentIsMissing() throws Exception {
        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new CustomException(ConsentErrorCode.REQUIRED_CONSENT_NOT_AGREED));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CONSENT400"))
                .andExpect(jsonPath("$.message").value("필수 약관에 모두 동의해 주세요."));
    }

    @Test
    void loginReturnsExpectedResponse() throws Exception {
        LoginResponse response = new LoginResponse(1L, 2L, "access-token", "refresh-token");
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "jiwoo.mom@email.com",
                                  "password": "password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("로그인에 성공했습니다."))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andExpect(jsonPath("$.data.selectedChildId").value(2))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    void loginReturnsAuth401ForUnknownEmailOrWrongPassword() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new CustomException(AuthErrorCode.INVALID_CREDENTIALS));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "jiwoo.mom@email.com",
                                  "password": "wrong-password"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH401"))
                .andExpect(jsonPath("$.message").value("이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void loginReturnsLoginSpecificAuth400ForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH400"))
                .andExpect(jsonPath("$.message").value("이메일과 비밀번호를 모두 입력해 주세요."));
    }

    @Test
    void refreshReturnsExpectedResponse() throws Exception {
        TokenRefreshResponse response = new TokenRefreshResponse(
                "new-access-token",
                "new-refresh-token"
        );
        when(authService.refresh(any(TokenRefreshRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "old-refresh-token"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("Access Token이 재발급되었습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    void refreshReturnsInvalidRefreshTokenError() throws Exception {
        when(authService.refresh(any(TokenRefreshRequest.class)))
                .thenThrow(new CustomException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "refreshToken": "invalid-refresh-token"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH401_INVALID_REFRESH_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 Refresh Token입니다."));
    }

    @Test
    void refreshReturnsSpecificErrorForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("AUTH400"))
                .andExpect(jsonPath("$.message").value("Refresh Token을 입력해 주세요."));
    }

    @Test
    void logoutReturnsExpectedResponse() throws Exception {
        when(authService.logout(1L)).thenReturn(new LogoutResponse());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .principal(() -> "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("로그아웃되었습니다."))
                .andExpect(jsonPath("$.data").isMap())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void verifyParentPinReturnsExpectedResponse() throws Exception {
        ParentPinVerifyResponse response = new ParentPinVerifyResponse("parent-mode-token");
        when(authService.verifyParentPin(any(), any(ParentPinVerifyRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/parent-pin/verify")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPin": "1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("보호자 인증에 성공했습니다."))
                .andExpect(jsonPath("$.data.parentModeToken").value("parent-mode-token"));
    }

    @Test
    void verifyParentPinReturnsParentPin403ForMismatch() throws Exception {
        when(authService.verifyParentPin(any(), any(ParentPinVerifyRequest.class)))
                .thenThrow(new CustomException(ParentPinErrorCode.PARENT_PIN_MISMATCH));

        mockMvc.perform(post("/api/v1/auth/parent-pin/verify")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPin": "9999"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PARENT_PIN403"))
                .andExpect(jsonPath("$.message").value("보호자 비밀번호가 올바르지 않습니다."));
    }

    @Test
    void verifyParentPinReturnsSpecificErrorForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/parent-pin/verify")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentPin\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("PARENT_PIN400"))
                .andExpect(jsonPath("$.message").value("보호자 비밀번호를 입력해 주세요."));
    }

    private SignupRequest validRequest() {
        return new SignupRequest(
                "jiwoo.mom@email.com",
                "password123!",
                "1234",
                new ChildSignupRequest("지우", "AGE_3_4"),
                List.of(
                        new ConsentAgreementRequest(1L, true),
                        new ConsentAgreementRequest(2L, true),
                        new ConsentAgreementRequest(3L, true),
                        new ConsentAgreementRequest(4L, true),
                        new ConsentAgreementRequest(5L, false)
                )
        );
    }
}
