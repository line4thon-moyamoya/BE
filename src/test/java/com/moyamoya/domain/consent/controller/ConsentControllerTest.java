package com.moyamoya.domain.consent.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyamoya.domain.consent.dto.response.ConsentResponse;
import com.moyamoya.domain.consent.entity.ConsentType;
import com.moyamoya.domain.consent.service.ConsentService;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ConsentControllerTest {

    @Mock
    private ConsentService consentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ConsentController(consentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getConsentsReturnsExpectedResponse() throws Exception {
        when(consentService.getConsents()).thenReturn(List.of(
                new ConsentResponse(1L, ConsentType.TERMS_OF_SERVICE, "서비스 이용약관 내용"),
                new ConsentResponse(2L, ConsentType.PRIVACY_POLICY, "개인정보 처리방침 내용")
        ));

        mockMvc.perform(get("/api/v1/consents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("동의 항목 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].consentId").value(1))
                .andExpect(jsonPath("$.data[0].consentType").value("TERMS_OF_SERVICE"))
                .andExpect(jsonPath("$.data[0].content").value("서비스 이용약관 내용"));
    }

    @Test
    void getConsentsReturnsEmptyListWhenNoItemsExist() throws Exception {
        when(consentService.getConsents()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/consents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("동의 항목 목록을 조회했습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void getConsentsReturnsCommon500ForUnexpectedError() throws Exception {
        when(consentService.getConsents()).thenThrow(new RuntimeException("database error"));

        mockMvc.perform(get("/api/v1/consents"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON500"))
                .andExpect(jsonPath("$.message")
                        .value("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
