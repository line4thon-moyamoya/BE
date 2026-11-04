package com.moyamoya.domain.child.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moyamoya.domain.child.dto.response.ChildResponse;
import com.moyamoya.domain.child.entity.AgeGroup;
import com.moyamoya.domain.child.entity.MoyaType;
import com.moyamoya.domain.child.exception.ChildExceptionHandler;
import com.moyamoya.domain.child.service.ChildService;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ChildControllerTest {
    private final ChildService service = mock(ChildService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ChildController(service))
            .setControllerAdvice(new ChildExceptionHandler(), new GlobalExceptionHandler()).build();

    @Test
    void returnsCreatedAndUsesPrincipalWithoutParentModeHeader() throws Exception {
        when(service.createChild(eq(7L), any()))
                .thenReturn(new ChildResponse(2L, "서아", AgeGroup.AGE_5, MoyaType.DEFAULT));
        mvc.perform(post("/api/v1/children").principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"서아\",\"ageGroup\":\"AGE_5\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("아이 프로필이 추가되었습니다."))
                .andExpect(jsonPath("$.data.childId").value(2))
                .andExpect(jsonPath("$.data.moyaType").value("DEFAULT"));
    }

    @Test
    void malformedJsonReturnsChild400() throws Exception {
        mvc.perform(post("/api/v1/children").principal(() -> "7")
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHILD400"));
        verifyNoInteractions(service);
    }
}
