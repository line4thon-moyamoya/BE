package com.moyamoya.domain.guardian.controller;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.guardian.dto.response.GuardianHomeResponse;
import com.moyamoya.domain.guardian.service.GuardianHomeService;
import com.moyamoya.global.config.SecurityConfig;
import com.moyamoya.global.config.WebMvcConfig;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import com.moyamoya.security.JwtAuthenticationFilter;
import com.moyamoya.security.JwtProperties;
import com.moyamoya.security.JwtTokenProvider;
import com.moyamoya.security.RestAccessDeniedHandler;
import com.moyamoya.security.RestAuthenticationEntryPoint;
import com.moyamoya.security.parentmode.ParentModeTokenInterceptor;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringJUnitWebConfig(GuardianHomeControllerSecurityTest.Config.class)
class GuardianHomeControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @Import({
            GuardianHomeController.class,
            SecurityConfig.class,
            WebMvcConfig.class,
            ParentModeTokenInterceptor.class,
            JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class,
            GlobalExceptionHandler.class
    })
    static class Config {

        @Bean
        GuardianHomeService guardianHomeService() {
            return org.mockito.Mockito.mock(GuardianHomeService.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return request -> null;
        }

        @Bean
        JwtTokenProvider jwtTokenProvider() {
            return new JwtTokenProvider(new JwtProperties(
                    "test-only-guardian-home-signing-key-at-least-32-bytes",
                    60_000L,
                    120_000L,
                    60_000L
            ));
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private GuardianHomeService guardianHomeService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(guardianHomeService);

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // Access Token + Parent Mode Token 모두 유효
    @Test
    void allowsGuardianHomeWhenBothTokensAreValid() throws Exception {
        when(guardianHomeService.getHome(7L, null))
                .thenReturn(GuardianHomeResponse.empty());

        mockMvc.perform(get("/api/v1/guardian/home")
                        .header("Authorization", accessToken())
                        .header(
                                ParentModeTokenInterceptor.HEADER_NAME,
                                parentModeToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message")
                        .value("보호자 홈 정보를 조회하였습니다."))
                .andExpect(jsonPath("$.data.children").isEmpty())
                .andExpect(jsonPath("$.data.selectedChildId").isEmpty());

        verify(guardianHomeService).getHome(7L, null);
    }

    // childId를 지정한 정상 요청
    @Test
    void allowsGuardianHomeWithChildId() throws Exception {
        GuardianHomeResponse response = new GuardianHomeResponse(
                List.of(
                        new GuardianHomeResponse.ChildInfo(2L, "서아")
                ),
                2L,
                "서아",
                java.time.LocalDate.of(2026, 11, 2),
                java.time.LocalDate.of(2026, 11, 8),
                4,
                1,
                2,
                List.of()
        );

        when(guardianHomeService.getHome(7L, 2L))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/guardian/home")
                        .header("Authorization", accessToken())
                        .header(
                                ParentModeTokenInterceptor.HEADER_NAME,
                                parentModeToken()
                        )
                        .param("childId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedChildId").value(2))
                .andExpect(jsonPath("$.data.childName").value("서아"))
                .andExpect(jsonPath("$.data.questionCount").value(4));

        verify(guardianHomeService).getHome(7L, 2L);
    }

    // Access Token 누락 -> AUTH401
    @Test
    void rejectsGuardianHomeWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/home")
                        .header(
                                ParentModeTokenInterceptor.HEADER_NAME,
                                parentModeToken()
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));

        verifyNoInteractions(guardianHomeService);
    }

    // Parent Mode Token 누락 -> PARENT_MODE401
    @Test
    void rejectsGuardianHomeWithoutParentModeToken() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/home")
                        .header("Authorization", accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PARENT_MODE401"));

        verifyNoInteractions(guardianHomeService);
    }

    // 잘못된 Parent Mode Token -> PARENT_MODE401
    @Test
    void rejectsGuardianHomeWithInvalidParentModeToken() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/home")
                        .header("Authorization", accessToken())
                        .header(
                                ParentModeTokenInterceptor.HEADER_NAME,
                                "invalid-token"
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PARENT_MODE401"));

        verifyNoInteractions(guardianHomeService);
    }

    // 다른 사용자의 Parent Mode Token -> PARENT_MODE403
    @Test
    void rejectsGuardianHomeWhenParentModeUserMismatches() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/home")
                        .header("Authorization", accessToken())
                        .header(
                                ParentModeTokenInterceptor.HEADER_NAME,
                                jwtTokenProvider.issueParentModeToken(8L)
                        ))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PARENT_MODE403"));

        verifyNoInteractions(guardianHomeService);
    }

    // 잘못된 Access Token -> AUTH401
    @Test
    void rejectsGuardianHomeWithInvalidAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/home")
                        .header("Authorization", "Bearer invalid-token")
                        .header(
                                ParentModeTokenInterceptor.HEADER_NAME,
                                parentModeToken()
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));

        verifyNoInteractions(guardianHomeService);
    }

    // Refresh Token을 Access Token으로 사용 -> AUTH401
    @Test
    void rejectsGuardianHomeWithRefreshToken() throws Exception {
        String refreshToken =
                jwtTokenProvider.issueTokens(7L).refreshToken();

        mockMvc.perform(get("/api/v1/guardian/home")
                        .header("Authorization", "Bearer " + refreshToken)
                        .header(
                                ParentModeTokenInterceptor.HEADER_NAME,
                                parentModeToken()
                        ))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));

        verifyNoInteractions(guardianHomeService);
    }

    // Access Token 발급
    private String accessToken() {
        return "Bearer "
                + jwtTokenProvider.issueTokens(7L).accessToken();
    }

    // 보호자 모드 토큰 발급
    private String parentModeToken() {
        return jwtTokenProvider.issueParentModeToken(7L);
    }
}
