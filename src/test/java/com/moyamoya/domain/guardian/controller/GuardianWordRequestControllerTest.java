package com.moyamoya.domain.guardian.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyamoya.domain.guardian.dto.response.GuardianWordRequestResponse;
import com.moyamoya.domain.guardian.dto.response.ResolveWordResponse;
import com.moyamoya.domain.guardian.exception.GuardianErrorCode;
import com.moyamoya.domain.guardian.service.GuardianWordRequestService;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
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
class GuardianWordRequestControllerTest {

    @Mock
    private GuardianWordRequestService guardianWordRequestService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new GuardianWordRequestController(guardianWordRequestService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getWordRequestsReturnsExpectedResponse() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 7, 10, 30);
        when(guardianWordRequestService.getWordRequests(1L, 2L))
                .thenReturn(List.of(
                        new GuardianWordRequestResponse(
                                31L,
                                "저끔",
                                null,
                                "https://example.com/presigned-audio",
                                "PENDING",
                                createdAt,
                                null
                        )
                ));

        mockMvc.perform(get("/api/v1/guardian/word-requests")
                        .principal(() -> "1")
                        .param("childId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("물어볼 단어 요청 목록을 조회하였습니다."))
                .andExpect(jsonPath("$.data[0].wordRequestId").value(31))
                .andExpect(jsonPath("$.data[0].recognizedText").value("저끔"))
                .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        verify(guardianWordRequestService).getWordRequests(1L, 2L);
    }

    @Test
    void getWordRequestsReturnsEmptyList() throws Exception {
        when(guardianWordRequestService.getWordRequests(1L, 2L))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/guardian/word-requests")
                        .principal(() -> "1")
                        .param("childId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(guardianWordRequestService).getWordRequests(1L, 2L);
    }

    @Test
    void resolveWordRequestReturnsExpectedResponse() throws Exception {
        LocalDateTime resolvedAt = LocalDateTime.of(2026, 11, 7, 11, 0);
        when(guardianWordRequestService.resolveWordRequest(1L, 31L, "적금"))
                .thenReturn(new ResolveWordResponse(
                        31L,
                        "저끔",
                        "적금",
                        "RESOLVED",
                        resolvedAt
                ));

        mockMvc.perform(patch("/api/v1/guardian/word-requests/31/resolve")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "word": "적금"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("물어볼 단어 요청을 해결하였습니다."))
                .andExpect(jsonPath("$.data.wordRequestId").value(31))
                .andExpect(jsonPath("$.data.correctedWord").value("적금"))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));

        verify(guardianWordRequestService).resolveWordRequest(1L, 31L, "적금");
    }

    @Test
    void resolveWordRequestReturnsValidationErrorForBlankWord() throws Exception {
        mockMvc.perform(patch("/api/v1/guardian/word-requests/31/resolve")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "word": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("GLOBAL400"))
                .andExpect(jsonPath("$.message").value("word: 알려줄 단어를 입력해주세요."));
    }

    @Test
    void resolveWordRequestReturnsGuardian403WhenForbidden() throws Exception {
        when(guardianWordRequestService.resolveWordRequest(anyLong(), eq(31L), eq("적금")))
                .thenThrow(new CustomException(GuardianErrorCode.ACCESS_FORBIDDEN));

        mockMvc.perform(patch("/api/v1/guardian/word-requests/31/resolve")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "word": "적금"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("GUARDIAN403"))
                .andExpect(jsonPath("$.message").value("해당 아이의 정보에 접근할 권한이 없습니다."));
    }

    @Test
    void resolveWordRequestReturns404WhenNotFound() throws Exception {
        when(guardianWordRequestService.resolveWordRequest(1L, 31L, "적금"))
                .thenThrow(new CustomException(
                        GuardianErrorCode.WORD_REQUEST_NOT_FOUND
                ));

        mockMvc.perform(patch("/api/v1/guardian/word-requests/31/resolve")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"word": "적금"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("GUARDIAN404_WORD_REQUEST"));
    }

    @Test
    void resolveWordRequestReturns409WhenAlreadyResolved() throws Exception {
        when(guardianWordRequestService.resolveWordRequest(1L, 31L, "적금"))
                .thenThrow(new CustomException(
                        GuardianErrorCode.WORD_REQUEST_ALREADY_RESOLVED
                ));

        mockMvc.perform(patch("/api/v1/guardian/word-requests/31/resolve")
                        .principal(() -> "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"word": "적금"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code")
                        .value("GUARDIAN409_WORD_REQUEST_RESOLVED"));
    }

    @Test
    void getWordRequestsReturns400ForInvalidChildId() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/word-requests")
                        .principal(() -> "1")
                        .param("childId", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(guardianWordRequestService);
    }
}
