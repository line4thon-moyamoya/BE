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
import com.moyamoya.domain.guardian.service.GuardianWordRequestService;
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

@SpringJUnitWebConfig(GuardianWordRequestControllerSecurityTest.Config.class)
class GuardianWordRequestControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @Import({
            GuardianWordRequestController.class,
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
        GuardianWordRequestService guardianWordRequestService() {
            return org.mockito.Mockito.mock(GuardianWordRequestService.class);
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
                    "test-only-guardian-signing-key-at-least-32-bytes",
                    60_000L,
                    120_000L,
                    60_000L
            ));
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private GuardianWordRequestService guardianWordRequestService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(guardianWordRequestService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void allowsGuardianRequestWhenBothTokensAreValid() throws Exception {
        when(guardianWordRequestService.getWordRequests(7L, 2L)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/guardian/word-requests")
                        .header("Authorization", accessToken())
                        .header(ParentModeTokenInterceptor.HEADER_NAME, parentModeToken())
                        .param("childId", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());

        verify(guardianWordRequestService).getWordRequests(7L, 2L);
    }

    @Test
    void rejectsGuardianRequestWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/word-requests")
                        .header(ParentModeTokenInterceptor.HEADER_NAME, parentModeToken())
                        .param("childId", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));

        verifyNoInteractions(guardianWordRequestService);
    }

    @Test
    void rejectsGuardianRequestWithoutParentModeToken() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/word-requests")
                        .header("Authorization", accessToken())
                        .param("childId", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PARENT_MODE401"));

        verifyNoInteractions(guardianWordRequestService);
    }

    @Test
    void rejectsGuardianRequestWithInvalidParentModeToken() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/word-requests")
                        .header("Authorization", accessToken())
                        .header(ParentModeTokenInterceptor.HEADER_NAME, "invalid-token")
                        .param("childId", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PARENT_MODE401"));

        verifyNoInteractions(guardianWordRequestService);
    }

    @Test
    void rejectsGuardianRequestWhenParentModeUserMismatches() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/word-requests")
                        .header("Authorization", accessToken())
                        .header(ParentModeTokenInterceptor.HEADER_NAME, jwtTokenProvider.issueParentModeToken(8L))
                        .param("childId", "2"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PARENT_MODE403"));

        verifyNoInteractions(guardianWordRequestService);
    }

    @Test
    void rejectsGuardianRequestWithInvalidAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/word-requests")
                        .header("Authorization", "Bearer invalid-token")
                        .header(
                                ParentModeTokenInterceptor.HEADER_NAME,
                                parentModeToken()
                        )
                        .param("childId", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));

        verifyNoInteractions(guardianWordRequestService);
    }

    @Test
    void rejectsGuardianRequestWithRefreshToken() throws Exception {
        String refreshToken =
                jwtTokenProvider.issueTokens(7L).refreshToken();

        mockMvc.perform(get("/api/v1/guardian/word-requests")
                        .header("Authorization", "Bearer " + refreshToken)
                        .header(
                                ParentModeTokenInterceptor.HEADER_NAME,
                                parentModeToken()
                        )
                        .param("childId", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));

        verifyNoInteractions(guardianWordRequestService);
    }

    private String accessToken() {
        return "Bearer " + jwtTokenProvider.issueTokens(7L).accessToken();
    }

    private String parentModeToken() {
        return jwtTokenProvider.issueParentModeToken(7L);
    }
}
