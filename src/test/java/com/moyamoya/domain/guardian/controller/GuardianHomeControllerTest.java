package com.moyamoya.domain.guardian.controller;

import com.moyamoya.domain.guardian.dto.response.GuardianHomeResponse;
import com.moyamoya.domain.guardian.dto.response.UpdateSelectedChildResponse;
import com.moyamoya.domain.guardian.exception.GuardianErrorCode;
import com.moyamoya.domain.guardian.service.GuardianHomeService;
import com.moyamoya.global.exception.CustomException;
import com.moyamoya.global.exception.GlobalExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GuardianHomeControllerTest {

    @Mock
    private GuardianHomeService service;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new GuardianHomeController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsHomeWithFirstChildWhenChildIdOmitted() throws Exception {
        GuardianHomeResponse response = new GuardianHomeResponse(
                List.of(
                        new GuardianHomeResponse.ChildInfo(1L, "지우"),
                        new GuardianHomeResponse.ChildInfo(2L, "서아")
                ),
                1L,
                "지우",
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 8),
                12,
                3,
                5,
                List.of(
                        new GuardianHomeResponse.PendingRequestPreview(
                                31L, "극정감자"
                        )
                )
        );

        when(service.getHome(7L, null)).thenReturn(response);

        mvc.perform(get("/api/v1/guardian/home")
                        .principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message")
                        .value("보호자 홈 정보를 조회하였습니다."))
                .andExpect(jsonPath("$.data.children.length()").value(2))
                .andExpect(jsonPath("$.data.selectedChildId").value(1))
                .andExpect(jsonPath("$.data.childName").value("지우"))
                .andExpect(jsonPath("$.data.questionCount").value(12))
                .andExpect(jsonPath("$.data.unrecognizedCount").value(3))
                .andExpect(jsonPath("$.data.pendingRequestCount").value(5))
                .andExpect(jsonPath("$.data.pendingRequestPreview[0].wordRequestId")
                        .value(31));

        verify(service).getHome(7L, null);
    }

    @Test
    void returnsSelectedChildWhenChildIdProvided() throws Exception {
        GuardianHomeResponse response = new GuardianHomeResponse(
                List.of(
                        new GuardianHomeResponse.ChildInfo(1L, "지우"),
                        new GuardianHomeResponse.ChildInfo(2L, "서아")
                ),
                2L,
                "서아",
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 8),
                4,
                1,
                2,
                List.of()
        );

        when(service.getHome(7L, 2L)).thenReturn(response);

        mvc.perform(get("/api/v1/guardian/home")
                        .principal(() -> "7")
                        .param("childId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedChildId").value(2))
                .andExpect(jsonPath("$.data.childName").value("서아"))
                .andExpect(jsonPath("$.data.questionCount").value(4));

        verify(service).getHome(7L, 2L);
    }

    @Test
    void returnsEmptyHomeWhenNoChildren() throws Exception {
        when(service.getHome(7L, null))
                .thenReturn(GuardianHomeResponse.empty());

        mvc.perform(get("/api/v1/guardian/home")
                        .principal(() -> "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.children.length()").value(0))
                .andExpect(jsonPath("$.data.selectedChildId").isEmpty())
                .andExpect(jsonPath("$.data.questionCount").value(0))
                .andExpect(jsonPath("$.data.pendingRequestPreview.length()")
                        .value(0));

        verify(service).getHome(7L, null);
    }

    @Test
    void invalidChildIdReturnsGlobal400() throws Exception {
        mvc.perform(get("/api/v1/guardian/home")
                        .principal(() -> "7")
                        .param("childId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL400"));

        verifyNoInteractions(service);
    }

    @Test
    void otherUsersChildReturnsGuardian403() throws Exception {
        when(service.getHome(7L, 99L))
                .thenThrow(new CustomException(
                        GuardianErrorCode.ACCESS_FORBIDDEN
                ));

        mvc.perform(get("/api/v1/guardian/home")
                        .principal(() -> "7")
                        .param("childId", "99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GUARDIAN403"));

        verify(service).getHome(7L, 99L);
    }

    @Test
    void updateSelectedChildReturnsUpdatedId() throws Exception {
        when(service.updateSelectedChild(7L, 2L))
                .thenReturn(new UpdateSelectedChildResponse(2L));

        mvc.perform(patch("/api/v1/guardian/home/selected-child")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "childId": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("최근 선택 아이가 변경되었습니다."))
                .andExpect(jsonPath("$.data.selectedChildId").value(2));

        verify(service).updateSelectedChild(7L, 2L);
    }

    @Test
    void updateSelectedChildReturnsGlobal400WhenChildIdMissing() throws Exception {
        mvc.perform(patch("/api/v1/guardian/home/selected-child")
                        .principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL400"));

        verifyNoInteractions(service);
    }
}
