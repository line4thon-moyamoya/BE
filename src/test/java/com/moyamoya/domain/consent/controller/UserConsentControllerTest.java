package com.moyamoya.domain.consent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyamoya.domain.consent.dto.request.ConsentStatusUpdateRequest;
import com.moyamoya.domain.consent.dto.response.ConsentStatusResponse;
import com.moyamoya.domain.consent.entity.ConsentType;
import com.moyamoya.domain.consent.exception.ConsentErrorCode;
import com.moyamoya.domain.consent.service.ConsentService;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class UserConsentControllerTest {

    @Mock
    private ConsentService consentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserConsentController(consentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void updateVoiceProcessingConsentReturnsChangedStatus() throws Exception {
        when(consentService.updateVoiceProcessingConsent(
                eq(1L),
                any(ConsentStatusUpdateRequest.class)
        )).thenReturn(new ConsentStatusResponse(ConsentType.VOICE_PROCESSING, false));

        mockMvc.perform(patch("/api/v1/users/me/consents/VOICE_PROCESSING")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isAgreed": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("음성 수집 동의 상태가 변경되었습니다."))
                .andExpect(jsonPath("$.data.consentType").value("VOICE_PROCESSING"))
                .andExpect(jsonPath("$.data.isAgreed").value(false));
    }

    @Test
    void updateVoiceProcessingConsentReturns400WhenStatusIsMissing() throws Exception {
        when(consentService.updateVoiceProcessingConsent(1L, null))
                .thenThrow(new CustomException(ConsentErrorCode.INVALID_CONSENT_REQUEST));

        mockMvc.perform(patch("/api/v1/users/me/consents/VOICE_PROCESSING")
                        .principal(() -> "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CONSENT400_INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("동의 여부를 입력해 주세요."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void updateVoiceProcessingConsentReturns404WhenRecordDoesNotExist() throws Exception {
        when(consentService.updateVoiceProcessingConsent(
                eq(1L),
                any(ConsentStatusUpdateRequest.class)
        )).thenThrow(new CustomException(ConsentErrorCode.CONSENT_RECORD_NOT_FOUND));

        mockMvc.perform(patch("/api/v1/users/me/consents/VOICE_PROCESSING")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "isAgreed": false
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("CONSENT_RECORD404"))
                .andExpect(jsonPath("$.message").value("사용자의 동의 내역을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
