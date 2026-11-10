package com.moyamoya.domain.child.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.moyamoya.domain.child.dto.request.InitialChildProfileRequest;
import com.moyamoya.domain.child.dto.response.InitialChildProfileResponse;
import com.moyamoya.domain.child.entity.MoyaType;
import com.moyamoya.domain.child.service.ChildService;
import com.moyamoya.global.config.CorsConfig;
import com.moyamoya.global.config.SecurityConfig;
import com.moyamoya.global.config.WebMvcConfig;
import com.moyamoya.security.JwtAuthenticationFilter;
import com.moyamoya.security.JwtTokenProvider;
import com.moyamoya.security.RestAccessDeniedHandler;
import com.moyamoya.security.RestAuthenticationEntryPoint;
import com.moyamoya.security.parentmode.ParentModeTokenInterceptor;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChildController.class)
@Import({SecurityConfig.class, CorsConfig.class, WebMvcConfig.class,
        JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, ParentModeTokenInterceptor.class})
class InitialChildProfileControllerTest {
    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ChildService service;

    @MockitoBean
    private JwtTokenProvider tokens;

    @MockitoBean
    private JpaMetamodelMappingContext jpaMappingContext;

    @BeforeEach
    void setUp() {
        when(tokens.getUserIdFromAccessToken("access-token")).thenReturn(7L);
    }

    @Test
    void allowsInitialSetupWithAccessTokenAndNoParentModeToken() throws Exception {
        when(service.setupInitialProfile(7L, 2L, new InitialChildProfileRequest("CAR")))
                .thenReturn(new InitialChildProfileResponse(2L, MoyaType.CAR));
        mvc.perform(patch("/api/v1/children/2/initial-profile")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"moyaType\":\"CAR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("초기 아이 프로필이 설정되었습니다."))
                .andExpect(jsonPath("$.data.childId").value(2))
                .andExpect(jsonPath("$.data.moyaType").value("CAR"));
        verify(service).setupInitialProfile(7L, 2L, new InitialChildProfileRequest("CAR"));
        verify(tokens, never()).getUserIdFromParentModeToken(anyString());
    }

    @Test
    void rejectsMissingAccessToken() throws Exception {
        mvc.perform(patch("/api/v1/children/2/initial-profile")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"moyaType\":\"CAR\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));
        verifyNoInteractions(service);
    }

    @Test
    void rejectsInvalidAccessTokenEvenWithParentModeToken() throws Exception {
        when(tokens.getUserIdFromAccessToken("invalid")).thenThrow(new JwtException("invalid"));
        mvc.perform(patch("/api/v1/children/2/initial-profile")
                        .header("Authorization", "Bearer invalid")
                        .header("X-Parent-Mode-Token", "parent-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"moyaType\":\"CAR\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{", "[]"})
    void rejectsMissingOrMalformedBody(String body) throws Exception {
        mvc.perform(patch("/api/v1/children/2/initial-profile")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void existingProfileEditStillRequiresParentModeToken() throws Exception {
        mvc.perform(patch("/api/v1/children/2")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"지우\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PARENT_MODE401"));
        verifyNoInteractions(service);
    }
}
