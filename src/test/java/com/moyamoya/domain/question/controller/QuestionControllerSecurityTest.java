package com.moyamoya.domain.question.controller;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyamoya.domain.question.dto.response.WordRequestSaveResponse;
import com.moyamoya.domain.question.service.QuestionConfirmService;
import com.moyamoya.domain.question.service.QuestionService;
import com.moyamoya.domain.question.service.WordRequestSaveService;
import com.moyamoya.global.config.SecurityConfig;
import com.moyamoya.global.exception.GlobalExceptionHandler;
import com.moyamoya.security.JwtAuthenticationFilter;
import com.moyamoya.security.JwtProperties;
import com.moyamoya.security.JwtTokenProvider;
import com.moyamoya.security.RestAccessDeniedHandler;
import com.moyamoya.security.RestAuthenticationEntryPoint;
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

@SpringJUnitWebConfig(QuestionControllerSecurityTest.Config.class)
class QuestionControllerSecurityTest {

    @Configuration
    @EnableWebMvc
    @Import({
            QuestionController.class,
            SecurityConfig.class,
            JwtAuthenticationFilter.class,
            RestAuthenticationEntryPoint.class,
            RestAccessDeniedHandler.class,
            GlobalExceptionHandler.class
    })
    static class Config {
        @Bean
        QuestionService questionService() {
            return org.mockito.Mockito.mock(QuestionService.class);
        }

        @Bean
        QuestionConfirmService questionConfirmService() {
            return org.mockito.Mockito.mock(QuestionConfirmService.class);
        }

        @Bean
        WordRequestSaveService wordRequestSaveService() {
            return org.mockito.Mockito.mock(WordRequestSaveService.class);
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
                    "test-only-question-signing-key-at-least-32-bytes",
                    60_000L,
                    120_000L,
                    60_000L
            ));
        }
    }

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private WordRequestSaveService wordRequestSaveService;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(wordRequestSaveService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void childModeApiAllowsAccessTokenWithoutParentModeToken() throws Exception {
        when(wordRequestSaveService.save(7L, "session-1"))
                .thenReturn(new WordRequestSaveResponse(31L, "PENDING", "WORD_REQUEST_SAVED"));

        mockMvc.perform(post("/api/v1/questions/session-1/word-request")
                        .header("Authorization", accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("물어볼 단어로 저장되었습니다."))
                .andExpect(jsonPath("$.data.wordRequestId").value(31));

        verify(wordRequestSaveService).save(7L, "session-1");
    }

    @Test
    void childModeApiRejectsMissingAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/questions/session-1/word-request"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401"));

        verifyNoInteractions(wordRequestSaveService);
    }

    private String accessToken() {
        return "Bearer " + jwtTokenProvider.issueTokens(7L).accessToken();
    }
}
